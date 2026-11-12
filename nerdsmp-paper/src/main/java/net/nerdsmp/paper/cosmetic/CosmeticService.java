package net.nerdsmp.paper.cosmetic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EntitlementService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.player.PlayerSettingsService;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Companions, trails, titles, auras, finishers, supporter badges, name colours, emotes, wardrobe presets and keepsakes.
 *
 * <p>Ownership comes from store entitlements (see {@link CosmeticCatalog}), read when a player joins and
 * every minute after. The chosen look is saved per account. Companions are block sculptures, in the style of
 * the armor-stand pets on Hypixel and Mineplex: each block of the model in companion-models.yml is a small
 * block display entity. Displays have no hitbox, no AI and are never saved to the world. Every two ticks this
 * service moves the whole sculpture and animates its legs, tails, wings and flames, and the client glides
 * between frames. Players can hide other players' companions and trails in /settings.
 */
public final class CosmeticService implements Listener, AutoCloseable {
    public static final int PRESETS = 3;
    private static final long REFRESH_MILLIS = 60_000L;
    private static final long EMOTE_COOLDOWN_MILLIS = 5_000L;
    private static final Duration KEEPSAKE_COOLDOWN = Duration.ofDays(1);
    private static final double VIEW_DISTANCE = 40.0D;

    /** How a player's name looks in chat, the tab list and above their head. */
    public record Style(String badge, NamedTextColor nameColor, boolean supporter, String tierName) {
        static final Style PLAIN = new Style("", null, false, "");
    }

    /** The saved look: which companion, trail, title, aura and finisher are on, and the chosen name colour. */
    public record Loadout(String companion, String trail, String nameColor, String title, String aura, String finisher) {
        static final Loadout EMPTY = new Loadout("", "", "", "", "", "");

        public Loadout(String companion, String trail, String nameColor) { this(companion, trail, nameColor, "", "", ""); }

        Loadout withCompanion(String slug) { return new Loadout(slug, trail, nameColor, title, aura, finisher); }
        Loadout withTrail(String slug) { return new Loadout(companion, slug, nameColor, title, aura, finisher); }
        Loadout withNameColor(String colour) { return new Loadout(companion, trail, colour, title, aura, finisher); }
        Loadout withTitle(String slug) { return new Loadout(companion, trail, nameColor, slug, aura, finisher); }
        Loadout withAura(String slug) { return new Loadout(companion, trail, nameColor, title, slug, finisher); }
        Loadout withFinisher(String slug) { return new Loadout(companion, trail, nameColor, title, aura, slug); }
    }

    /** The kinds of cosmetic worn one at a time from the wardrobe's pages. */
    public enum Kind { TITLE, AURA, FINISHER }

    private final NerdSmpPlugin plugin;
    private final CosmeticCatalog catalog;
    private final NamespacedKey companionKey;
    private final NamespacedKey keepsakeKey;
    private final Map<UUID, CosmeticCatalog.Ownership> owned = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextRefresh = new ConcurrentHashMap<>();
    private final Map<UUID, Loadout> loadouts = new HashMap<>();
    private final Map<UUID, Sculpture> active = new HashMap<>();
    private final Map<String, SculptureModel> models;
    private final Map<UUID, Location> lastSeen = new HashMap<>();
    private final Map<UUID, Long> lastEmote = new HashMap<>();
    private final Map<UUID, Long> respawnAt = new HashMap<>();
    private final Map<UUID, Double> lastBounce = new HashMap<>();
    private BukkitTask task;
    private long tick;

    public CosmeticService(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        this.catalog = load(plugin.getConfig().getConfigurationSection("cosmetics"), plugin);
        this.companionKey = new NamespacedKey(plugin, "companion_owner");
        this.keepsakeKey = new NamespacedKey(plugin, "keepsake_owner");
        this.models = loadModels(plugin);
        this.bond = new CompanionBond(plugin);
    }

    /** Companion bonds: looks that unlock with time together, never stats. */
    private final CompanionBond bond;
    public CompanionBond bond() { return bond; }

    /** One companion out in the world: its block displays, in the same order as the model's parts. */
    private record Sculpture(SculptureModel model, CosmeticCatalog.Companion companion, List<BlockDisplay> parts) {
        boolean valid(World world) {
            return !parts.isEmpty() && parts.getFirst().isValid() && parts.getFirst().getWorld().equals(world);
        }
        Location location() { return parts.getFirst().getLocation(); }
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::step, 20L, 2L);
        Bukkit.getOnlinePlayers().forEach(this::joined);
    }

    public CosmeticCatalog catalog() { return catalog; }

    // --- Ownership --------------------------------------------------------------------------

    public CosmeticCatalog.Ownership ownership(Player player) {
        return owned.getOrDefault(player.getUniqueId(), CosmeticCatalog.Ownership.NONE);
    }

    /** Re-reads a player's purchases, telling them about anything new. Called after the store grants something. */
    public void refresh(Player player) {
        AccountId account = account(player);
        if (account == null) return;
        UUID id = player.getUniqueId();
        nextRefresh.put(id, System.currentTimeMillis() + REFRESH_MILLIS);
        plugin.entitlements().activeEntitlements(account, Instant.now()).thenAccept(entitlements -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            List<String> products = entitlements.stream().map(EntitlementService.Entitlement::productId).toList();
            CosmeticCatalog.Ownership next = catalog.ownership(products);
            CosmeticCatalog.Ownership previous = owned.put(id, next);
            if (previous != null) announce(player, previous, next);
            applyLoadout(player);
            plugin.presentation().refresh(player);
        })).exceptionally(error -> {
            plugin.getLogger().log(Level.WARNING, "Could not read cosmetics for " + player.getName(), error);
            return null;
        });
    }

    private void announce(Player player, CosmeticCatalog.Ownership before, CosmeticCatalog.Ownership after) {
        for (String slug : after.companions()) {
            if (!before.companions().contains(slug)) catalog.companion(slug).ifPresent(c ->
                Chat.success(player, "Your <white>" + c.name() + "</white> companion is ready. Summon it with <white>/companion " + c.slug() + "</white>."));
        }
        for (String slug : after.trails()) {
            if (!before.trails().contains(slug)) catalog.trail(slug).ifPresent(t ->
                Chat.success(player, "Your <white>" + t.name() + "</white> trail is ready. Wear it with <white>/trail " + t.slug() + "</white>."));
        }
        for (String slug : after.titles()) {
            if (!before.titles().contains(slug)) catalog.title(slug).ifPresent(t ->
                Chat.success(player, "New title: <" + t.color() + ">" + t.name() + "</" + t.color() + ">. Wear it with <white>/cosmetics title " + t.slug() + "</white>."));
        }
        for (String slug : after.auras()) {
            if (!before.auras().contains(slug)) catalog.aura(slug).ifPresent(a ->
                Chat.success(player, "New aura: <white>" + a.name() + "</white>. Wear it with <white>/cosmetics aura " + a.slug() + "</white>."));
        }
        for (String slug : after.finishers()) {
            if (!before.finishers().contains(slug)) catalog.finisher(slug).ifPresent(f ->
                Chat.success(player, "New finisher: <white>" + f.name() + "</white>. Wear it with <white>/cosmetics finisher " + f.slug() + "</white>."));
        }
        if (after.tier().isPresent() && !after.tier().equals(before.tier())) {
            Chat.success(player, "Thank you for supporting Aerborne! Your " + after.tier().get().badge() + " <white>"
                + after.tier().get().name() + "</white> perks are on. Open <white>/cosmetics</white> to choose your look.");
        }
    }

    // --- The saved look ---------------------------------------------------------------------

    public Loadout loadout(Player player) { return loadouts.getOrDefault(player.getUniqueId(), Loadout.EMPTY); }

    private Loadout readLoadout(AccountId account) {
        return plugin.playerData().read(account, yaml -> new Loadout(yaml.getString("cosmetics.companion", ""),
            yaml.getString("cosmetics.trail", ""), yaml.getString("cosmetics.name-color", ""), yaml.getString("cosmetics.title", ""),
            yaml.getString("cosmetics.aura", ""), yaml.getString("cosmetics.finisher", "")));
    }

    private void save(Player player, Loadout next) {
        loadouts.put(player.getUniqueId(), next);
        AccountId account = account(player);
        if (account == null) return;
        try {
            plugin.playerData().update(account, yaml -> {
                yaml.set("cosmetics.companion", next.companion());
                yaml.set("cosmetics.trail", next.trail());
                yaml.set("cosmetics.name-color", next.nameColor());
                yaml.set("cosmetics.title", next.title());
                yaml.set("cosmetics.aura", next.aura());
                yaml.set("cosmetics.finisher", next.finisher());
            });
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "Could not save cosmetics for " + player.getName(), error);
        }
    }

    /** Summons the companion and trail a player has chosen, if they still own them. */
    private void applyLoadout(Player player) {
        Loadout look = loadout(player);
        CosmeticCatalog.Ownership own = ownership(player);
        if (!look.companion().isEmpty() && own.companions().contains(look.companion())) {
            if (!active.containsKey(player.getUniqueId())) respawnAt.put(player.getUniqueId(), 0L);
        } else {
            despawn(player.getUniqueId());
        }
    }

    // --- Commands ---------------------------------------------------------------------------

    public void summon(Player player, String slug) {
        Optional<CosmeticCatalog.Companion> companion = catalog.companion(slug);
        if (companion.isEmpty() || CosmeticCatalog.hidden(companion.get(), ownership(player))) {
            Chat.error(player, "No companion is called that. Choose one with <white>/companion</white>.");
            return;
        }
        if (!ownership(player).companions().contains(companion.get().slug())) {
            Chat.error(player, "You don't have " + companion.get().name() + " yet. Companions come from the store at aerborne.net/store.");
            return;
        }
        save(player, loadout(player).withCompanion(companion.get().slug()));
        despawn(player.getUniqueId());
        respawnAt.put(player.getUniqueId(), 0L);
        Chat.success(player, companion.get().name() + " is here.");
    }

    public void dismiss(Player player) {
        Loadout look = loadout(player);
        if (look.companion().isEmpty() && !active.containsKey(player.getUniqueId())) {
            Chat.notice(player, "You don't have a companion out.");
            return;
        }
        save(player, look.withCompanion(""));
        despawn(player.getUniqueId());
        Chat.success(player, "Your companion is resting.");
    }

    public void wearTrail(Player player, String slug) {
        Loadout look = loadout(player);
        if (slug == null || slug.isEmpty() || slug.equalsIgnoreCase("off")) {
            save(player, look.withTrail(""));
            Chat.success(player, "Trail off.");
            return;
        }
        Optional<CosmeticCatalog.Trail> trail = catalog.trail(slug);
        if (trail.isEmpty()) {
            Chat.error(player, "No trail is called that. Choose one with <white>/trail</white>.");
            return;
        }
        if (!ownership(player).trails().contains(trail.get().slug())) {
            Chat.error(player, "You don't have the " + trail.get().name() + " trail yet. Trails come from the store at aerborne.net/store.");
            return;
        }
        save(player, look.withTrail(trail.get().slug()));
        Chat.success(player, "Wearing the <white>" + trail.get().name() + "</white> trail.");
    }

    /** Wears a title, aura or finisher the player owns; {@code off} takes it off. */
    public void wear(Player player, Kind kind, String slug) {
        Loadout look = loadout(player);
        CosmeticCatalog.Ownership own = ownership(player);
        String noun = kind.name().toLowerCase(Locale.ROOT);
        if (slug == null || slug.isEmpty() || slug.equalsIgnoreCase("off")) {
            save(player, switch (kind) { case TITLE -> look.withTitle(""); case AURA -> look.withAura(""); case FINISHER -> look.withFinisher(""); });
            plugin.presentation().refresh(player);
            Chat.success(player, net.nerdsmp.paper.presentation.VisualFormat.titleCase(noun) + " off.");
            return;
        }
        Optional<String> name = switch (kind) {
            case TITLE -> catalog.title(slug).filter(t -> own.titles().contains(t.slug())).map(CosmeticCatalog.Title::name);
            case AURA -> catalog.aura(slug).filter(a -> own.auras().contains(a.slug())).map(CosmeticCatalog.Aura::name);
            case FINISHER -> catalog.finisher(slug).filter(f -> own.finishers().contains(f.slug())).map(CosmeticCatalog.Finisher::name);
        };
        if (name.isEmpty()) {
            Chat.error(player, "You don't have a " + noun + " called that. See yours in <white>/cosmetics</white>.");
            return;
        }
        String chosen = CosmeticCatalog.normalise(slug);
        save(player, switch (kind) { case TITLE -> look.withTitle(chosen); case AURA -> look.withAura(chosen); case FINISHER -> look.withFinisher(chosen); });
        plugin.presentation().refresh(player);
        Chat.success(player, "Wearing the <white>" + name.get() + "</white> " + noun + ".");
    }

    /** The worn title as chat markup, or empty. */
    public String titleTag(Player player) {
        String worn = loadout(player).title();
        if (worn.isEmpty() || !ownership(player).titles().contains(worn)) return "";
        return catalog.title(worn).map(t -> "<" + t.color() + ">" + Chat.escape(t.name()) + "</" + t.color() + ">").orElse("");
    }

    /**
     * Grants whatever a feat unlocks (see {@link CosmeticCatalog#clearKeys}), once per account: the transaction id
     * is fixed, so earning it again changes nothing.
     */
    public void earn(Player player, java.util.Collection<String> keys) {
        AccountId account = account(player);
        if (account == null) return;
        List<String> products = catalog.earnedBy(keys);
        if (products.isEmpty()) return;
        List<java.util.concurrent.CompletableFuture<?>> grants = new ArrayList<>();
        for (String product : products)
            grants.add(plugin.entitlements().grant(account, product, "earned:" + product + ":" + account.value(), null).toCompletableFuture());
        java.util.concurrent.CompletableFuture.allOf(grants.toArray(java.util.concurrent.CompletableFuture[]::new))
            .whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) plugin.getLogger().log(Level.WARNING, "Could not grant earned cosmetics to " + player.getName(), error);
                if (player.isOnline()) refresh(player);
            }));
    }

    /** Moves to the next colour in the player's palette, ending at their tier's own colour. Returns the new choice. */
    public String cycleNameColor(Player player) {
        List<String> palette = ownership(player).palette();
        if (palette.isEmpty()) {
            Chat.error(player, "Name colours come with a supporter rank.");
            return "";
        }
        Loadout look = loadout(player);
        int index = palette.indexOf(look.nameColor());
        String next = index + 1 >= palette.size() ? "" : palette.get(index + 1);
        save(player, look.withNameColor(next));
        plugin.presentation().refresh(player);
        return next;
    }

    public void emote(Player player, String id) {
        Optional<CosmeticCatalog.Emote> emote = catalog.emote(id);
        if (emote.isEmpty()) {
            Chat.usage(player, "/emote <" + String.join("|", catalog.emoteIds()) + ">");
            return;
        }
        if (!ownership(player).emotes()) {
            Chat.error(player, "Emotes come with the Cloudkeeper and Starweaver ranks.");
            return;
        }
        if (!plugin.staff().canCommunicate(player.getUniqueId())) {
            Chat.error(player, "You can't use emotes while muted.");
            return;
        }
        long now = System.currentTimeMillis();
        if (lastEmote.getOrDefault(player.getUniqueId(), 0L) + EMOTE_COOLDOWN_MILLIS > now) {
            Chat.notice(player, "Wait a moment before your next emote.");
            return;
        }
        lastEmote.put(player.getUniqueId(), now);
        Style style = style(player);
        String line = style.badge() + " <" + colorName(style.nameColor()) + ">" + Chat.escape(player.getName()) + "</"
            + colorName(style.nameColor()) + "> <gray>" + emote.get().action() + ".</gray>";
        Particle particle = particle(emote.get().particle(), Particle.HAPPY_VILLAGER);
        Location above = player.getLocation().add(0, 2.2D, 0);
        for (Player viewer : player.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(player.getLocation()) > 24 * 24) continue;
            if (viewer != player && plugin.social().isIgnoring(viewer.getUniqueId(), player.getUniqueId())) continue;
            Chat.send(viewer, line);
            viewer.spawnParticle(particle, above, 8, 0.4D, 0.3D, 0.4D, 0.0D);
        }
    }

    // --- Presets and keepsake ---------------------------------------------------------------

    public Optional<Loadout> preset(Player player, int slot) {
        AccountId account = account(player);
        if (account == null || slot < 1 || slot > PRESETS) return Optional.empty();
        return plugin.playerData().read(account, yaml -> {
            String base = "cosmetics.presets." + slot;
            if (!yaml.isConfigurationSection(base)) return Optional.empty();
            return Optional.of(new Loadout(yaml.getString(base + ".companion", ""), yaml.getString(base + ".trail", ""),
                yaml.getString(base + ".name-color", ""), yaml.getString(base + ".title", ""), yaml.getString(base + ".aura", ""),
                yaml.getString(base + ".finisher", "")));
        });
    }

    public void savePreset(Player player, int slot) {
        AccountId account = account(player);
        if (account == null || slot < 1 || slot > PRESETS) return;
        if (!ownership(player).presets()) {
            Chat.error(player, "Wardrobe presets come with the Starweaver rank.");
            return;
        }
        Loadout look = loadout(player);
        plugin.playerData().update(account, yaml -> {
            String base = "cosmetics.presets." + slot;
            yaml.set(base + ".companion", look.companion());
            yaml.set(base + ".trail", look.trail());
            yaml.set(base + ".name-color", look.nameColor());
            yaml.set(base + ".title", look.title());
            yaml.set(base + ".aura", look.aura());
            yaml.set(base + ".finisher", look.finisher());
        });
        Chat.success(player, "Saved your current look to preset " + slot + ".");
    }

    public void applyPreset(Player player, int slot) {
        if (!ownership(player).presets()) {
            Chat.error(player, "Wardrobe presets come with the Starweaver rank.");
            return;
        }
        Optional<Loadout> preset = preset(player, slot);
        if (preset.isEmpty()) {
            Chat.notice(player, "Preset " + slot + " is empty. Save your current look to it first.");
            return;
        }
        CosmeticCatalog.Ownership own = ownership(player);
        Loadout look = preset.get();
        Loadout usable = new Loadout(own.companions().contains(look.companion()) ? look.companion() : "",
            own.trails().contains(look.trail()) ? look.trail() : "",
            own.palette().contains(look.nameColor()) ? look.nameColor() : "",
            own.titles().contains(look.title()) ? look.title() : "",
            own.auras().contains(look.aura()) ? look.aura() : "",
            own.finishers().contains(look.finisher()) ? look.finisher() : "");
        save(player, usable);
        despawn(player.getUniqueId());
        applyLoadout(player);
        plugin.presentation().refresh(player);
        Chat.success(player, "Wearing preset " + slot + ".");
    }

    public boolean keepsakeReady(Player player) {
        AccountId account = account(player);
        if (account == null) return false;
        long last = plugin.playerData().read(account, yaml -> yaml.getLong("cosmetics.keepsake-claimed-at", 0L));
        return Instant.ofEpochMilli(last).plus(KEEPSAKE_COOLDOWN).isBefore(Instant.now());
    }

    public void claimKeepsake(Player player) {
        AccountId account = account(player);
        if (account == null) return;
        if (!ownership(player).keepsake()) {
            Chat.error(player, "The Starweaver Lantern keepsake comes with the Starweaver rank.");
            return;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta() && account.value().toString().equals(
                item.getItemMeta().getPersistentDataContainer().get(keepsakeKey, PersistentDataType.STRING))) {
                Chat.notice(player, "You're already carrying your Starweaver Lantern.");
                return;
            }
        }
        if (!keepsakeReady(player)) {
            Chat.notice(player, "You can claim another Starweaver Lantern a day after your last one.");
            return;
        }
        ItemStack lantern = new ItemStack(Material.SOUL_LANTERN);
        ItemMeta meta = lantern.getItemMeta();
        meta.itemName(Component.text("Starweaver Lantern", NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(Component.text("A keepsake from the skies, holding a little starlight.", NamedTextColor.GRAY),
            Component.text("Decorative only. Thank you for supporting Aerborne.", NamedTextColor.DARK_GRAY)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(keepsakeKey, PersistentDataType.STRING, account.value().toString());
        lantern.setItemMeta(meta);
        if (!player.getInventory().addItem(lantern).isEmpty()) {
            Chat.error(player, "Make room in your inventory first.");
            return;
        }
        plugin.playerData().update(account, yaml -> yaml.set("cosmetics.keepsake-claimed-at", System.currentTimeMillis()));
        Chat.success(player, "Your Starweaver Lantern is in your inventory.");
    }

    // --- Name style -------------------------------------------------------------------------

    public Style style(Player player) {
        Optional<CosmeticCatalog.Tier> tier = ownership(player).tier();
        if (tier.isEmpty()) return Style.PLAIN;
        String chosen = loadout(player).nameColor();
        String colour = !chosen.isEmpty() && tier.get().palette().contains(chosen) ? chosen : tier.get().color();
        return new Style(tier.get().badge(), named(colour, NamedTextColor.GOLD), true, tier.get().name());
    }

    static NamedTextColor named(String name, NamedTextColor fallback) {
        NamedTextColor colour = name == null ? null : NamedTextColor.NAMES.value(name.toLowerCase(Locale.ROOT));
        return colour == null ? fallback : colour;
    }

    private static String colorName(NamedTextColor colour) {
        return colour == null ? "white" : NamedTextColor.NAMES.key(colour);
    }

    // --- Companions in the world ------------------------------------------------------------

    public boolean isCompanion(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(companionKey, PersistentDataType.STRING);
    }

    private void step() {
        tick++;
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (nextRefresh.getOrDefault(id, 0L) <= now && owned.containsKey(id)) refresh(player);
            try {
                moveCompanion(player, now);
                drawAura(player);
                drawTrail(player);
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING, "Cosmetics paused for " + player.getName(), error);
                despawn(id);
            }
        }
    }

    private boolean hidden(Player player) {
        return player.isDead() || plugin.spirits()!=null && plugin.spirits().ghost(player.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR || player.isInvisible()
            || player.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
    }

    private void moveCompanion(Player player, long now) {
        UUID id = player.getUniqueId();
        Loadout look = loadout(player);
        Optional<CosmeticCatalog.Companion> chosen = catalog.companion(look.companion());
        if (chosen.isEmpty() || !ownership(player).companions().contains(chosen.get().slug()) || hidden(player)) {
            despawn(id);
            return;
        }
        CosmeticCatalog.Companion companion = chosen.get();
        Sculpture sculpture = active.get(id);
        Location at = player.getLocation();
        FollowMath.Point target = FollowMath.target(at.getX(), at.getY(), at.getZ(), at.getYaw(), companion.flying(), tick);
        if (!companion.flying()) target = new FollowMath.Point(target.x(), ground(at.getWorld(), target), target.z(), target.yaw());
        if (sculpture == null || !sculpture.companion().equals(companion) || !sculpture.valid(at.getWorld())) {
            despawn(id);
            if (respawnAt.getOrDefault(id, 0L) > now) return;
            sculpture = spawn(player, companion, new Location(at.getWorld(), target.x(), target.y(), target.z(), target.yaw(), 0));
            if (sculpture == null) {
                respawnAt.put(id, now + 30_000L);
                return;
            }
            active.put(id, sculpture);
            return;
        }
        Location current = sculpture.location();
        FollowMath.Point here = new FollowMath.Point(current.getX(), current.getY() - lastBounce.getOrDefault(id, 0.0D),
            current.getZ(), current.getYaw());
        FollowMath.Point next = FollowMath.step(here, target, companion.flying());
        double dx = next.x() - here.x(), dz = next.z() - here.z();
        boolean moving = dx * dx + dz * dz > 0.0004D;
        double lift = companion.flying() ? 0.0D : SculptureMath.bounce(sculpture.model().bounce(), tick, moving, companion.scale());
        lastBounce.put(id, lift);
        Location to = bond.pose(player, new Location(at.getWorld(), next.x(), next.y() + lift, next.z(), next.yaw(), 0), tick);
        List<SculptureModel.Part> parts = sculpture.model().parts();
        for (int index = 0; index < parts.size(); index++) {
            BlockDisplay display = sculpture.parts().get(index);
            display.teleport(to);
            if (parts.get(index).animated()) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(3);
                display.setTransformation(transformation(sculpture.model(), parts.get(index), moving, companion));
            }
        }
        bond.decorate(player, companion, sculpture.parts(), to, viewers(player), tick);
        if (!companion.particle().isEmpty() && tick % 10 == 0) {
            Particle particle = particle(companion.particle(), null);
            if (particle != null) {
                Location spot = to.clone().add(0, 0.5D * companion.scale(), 0);
                for (Player viewer : viewers(player)) viewer.spawnParticle(particle, spot, 1, 0.2D, 0.2D, 0.2D, 0.0D);
            }
        }
    }

    private Transformation transformation(SculptureModel model, SculptureModel.Part part, boolean moving, CosmeticCatalog.Companion companion) {
        SculptureMath.Pose pose = SculptureMath.pose(model, part, tick, moving, companion.flying(), companion.scale());
        return new Transformation(pose.translation(), pose.rotation(), pose.scale(), new org.joml.Quaternionf());
    }

    /** The surface a walking companion stands on: the first solid block at or below the spot, else the owner's height. */
    private static double ground(World world, FollowMath.Point point) {
        int x = (int) Math.floor(point.x()), z = (int) Math.floor(point.z());
        int top = (int) Math.floor(point.y() + 0.5D);
        for (int y = top; y >= top - 4 && y >= world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            if (!block.isPassable()) return y + Math.max(0.0D, Math.min(1.0D, block.getBoundingBox().getMaxY() - y));
        }
        return point.y();
    }

    /** Builds the sculpture: one small block display per part of the model, all standing at the companion's spot. */
    private Sculpture spawn(Player owner, CosmeticCatalog.Companion companion, Location location) {
        SculptureModel model = models.get(companion.model());
        if (model == null) {
            plugin.getLogger().warning("Companion " + companion.slug() + " uses a model missing from companion-models.yml: " + companion.model());
            return null;
        }
        List<Player> hiddenFrom = new ArrayList<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer != owner && !plugin.settings().enabled(viewer.getUniqueId(), PlayerSettingsService.Setting.OTHERS_COSMETICS)) hiddenFrom.add(viewer);
        }
        List<BlockDisplay> displays = new ArrayList<>();
        try {
            for (SculptureModel.Part part : model.parts()) {
                org.bukkit.block.data.BlockData block = blockData(part.block());
                boolean first = displays.isEmpty();
                displays.add(location.getWorld().spawn(location, BlockDisplay.class, display -> {
                    display.getPersistentDataContainer().set(companionKey, PersistentDataType.STRING, owner.getUniqueId().toString());
                    display.setPersistent(false);
                    display.setBlock(block);
                    display.setTransformation(transformation(model, part, false, companion));
                    display.setTeleportDuration(2);
                    display.setInterpolationDuration(3);
                    display.setViewRange(0.75F);
                    if (part.glow()) display.setBrightness(new Display.Brightness(15, 15));
                    if (first && !companion.flying()) {
                        display.setShadowRadius((float) (0.35D * companion.scale()));
                        display.setShadowStrength(0.6F);
                    }
                    for (Player viewer : hiddenFrom) viewer.hideEntity(plugin, display);
                }));
            }
        } catch (IllegalArgumentException invalid) {
            displays.forEach(Entity::remove);
            plugin.getLogger().warning("Companion " + companion.slug() + " can't be built: " + invalid.getMessage());
            return null;
        }
        return new Sculpture(model, companion, List.copyOf(displays));
    }

    private static org.bukkit.block.data.BlockData blockData(String id) {
        Material material = Material.matchMaterial(id);
        if (material == null || !material.isBlock()) throw new IllegalArgumentException(id + " is not a block");
        return material.createBlockData();
    }

    private void despawn(UUID owner) {
        bond.despawn(owner);
        Sculpture sculpture = active.remove(owner);
        lastBounce.remove(owner);
        if (sculpture != null) sculpture.parts().forEach(part -> { if (part.isValid()) part.remove(); });
    }

    // --- Trails -----------------------------------------------------------------------------

    private void drawTrail(Player player) {
        UUID id = player.getUniqueId();
        Location now = player.getLocation();
        Location before = lastSeen.put(id, now);
        Optional<CosmeticCatalog.Trail> trail = catalog.trail(loadout(player).trail());
        if (trail.isEmpty() || !ownership(player).trails().contains(trail.get().slug()) || hidden(player) || before == null
            || !before.getWorld().equals(now.getWorld())) return;
        double dx = now.getX() - before.getX(), dz = now.getZ() - before.getZ();
        boolean moving = dx * dx + dz * dz > 0.0025D;
        double turned = Math.abs(((now.getYaw() - before.getYaw()) % 360 + 540) % 360 - 180);
        List<TrailPatterns.Puff> puffs = TrailPatterns.frame(trail.get().pattern(), tick, now.getYaw(), turned, moving);
        if (puffs.isEmpty()) return;
        Particle particle = particle(trail.get().particle(), Particle.END_ROD);
        List<Player> viewers = viewers(player);
        for (TrailPatterns.Puff puff : puffs) {
            Location spot = now.clone().add(puff.x(), puff.y(), puff.z());
            for (Player viewer : viewers) viewer.spawnParticle(particle, spot, puff.count(), puff.spread(), puff.spread(), puff.spread(), 0.0D);
        }
    }

    // --- Auras and finishers -----------------------------------------------------------------

    /** An aura shows while its wearer stands still; once they move, their trail has the stage. */
    private void drawAura(Player player) {
        Optional<CosmeticCatalog.Aura> aura = catalog.aura(loadout(player).aura());
        Location before = lastSeen.get(player.getUniqueId()), now = player.getLocation();
        if (aura.isEmpty() || !ownership(player).auras().contains(aura.get().slug()) || hidden(player) || before == null
            || !before.getWorld().equals(now.getWorld())) return;
        double dx = now.getX() - before.getX(), dz = now.getZ() - before.getZ();
        if (dx * dx + dz * dz > 0.0025D) return;
        List<TrailPatterns.Puff> puffs = AuraPatterns.aura(aura.get().pattern(), tick);
        if (puffs.isEmpty()) return;
        Particle particle = particle(aura.get().particle(), Particle.END_ROD);
        List<Player> viewers = viewers(player);
        for (TrailPatterns.Puff puff : puffs) {
            Location spot = now.clone().add(puff.x(), puff.y(), puff.z());
            for (Player viewer : viewers) viewer.spawnParticle(particle, spot, puff.count(), puff.spread(), puff.spread(), puff.spread(), 0.0D);
        }
    }

    /** A worn finisher goes off where its wearer's kill falls. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(org.bukkit.event.entity.EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || event.getEntity() == killer || hidden(killer)) return;
        Optional<CosmeticCatalog.Finisher> finisher = catalog.finisher(loadout(killer).finisher());
        if (finisher.isEmpty() || !ownership(killer).finishers().contains(finisher.get().slug())) return;
        Particle particle = particle(finisher.get().particle(), Particle.FIREWORK);
        Location at = event.getEntity().getLocation();
        List<Player> viewers = viewers(killer);
        for (TrailPatterns.Puff puff : AuraPatterns.finisher(finisher.get().pattern())) {
            Location spot = at.clone().add(puff.x(), puff.y(), puff.z());
            for (Player viewer : viewers) {
                if (viewer.getWorld().equals(spot.getWorld()))
                    viewer.spawnParticle(particle, spot, puff.count(), puff.spread(), puff.spread(), puff.spread(), 0.02D);
            }
        }
    }

    /** The owner plus nearby players who show other players' cosmetics. */
    private List<Player> viewers(Player owner) {
        List<Player> out = new ArrayList<>();
        out.add(owner);
        Location at = owner.getLocation();
        for (Player viewer : owner.getWorld().getPlayers()) {
            if (viewer == owner || !viewer.canSee(owner) || viewer.getLocation().distanceSquared(at) > VIEW_DISTANCE * VIEW_DISTANCE) continue;
            if (plugin.settings().enabled(viewer.getUniqueId(), PlayerSettingsService.Setting.OTHERS_COSMETICS)) out.add(viewer);
        }
        return out;
    }

    /** Applies a viewer's choice to every companion already out. */
    public void updateVisibility(Player viewer) {
        boolean show = plugin.settings().enabled(viewer.getUniqueId(), PlayerSettingsService.Setting.OTHERS_COSMETICS);
        active.forEach((owner, sculpture) -> {
            if (owner.equals(viewer.getUniqueId())) return;
            for (BlockDisplay part : sculpture.parts()) {
                if (!part.isValid()) continue;
                if (show) viewer.showEntity(plugin, part); else viewer.hideEntity(plugin, part);
            }
        });
    }

    // --- Events -----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> joined(event.getPlayer()), 40L);
    }

    private void joined(Player player) {
        if (!player.isOnline()) return;
        AccountId account = account(player);
        if (account == null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> joined(player), 40L);
            return;
        }
        loadouts.put(player.getUniqueId(), readLoadout(account));
        updateVisibility(player);
        refresh(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        despawn(id);
        owned.remove(id);
        nextRefresh.remove(id);
        loadouts.remove(id);
        lastSeen.remove(id);
        lastEmote.remove(id);
        respawnAt.remove(id);
        bond.forget(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) { despawn(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) { respawnAt.put(event.getPlayer().getUniqueId(), System.currentTimeMillis() + 1_000L); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) { despawn(event.getPlayer().getUniqueId()); }

    @Override
    public void close() {
        if (task != null) task.cancel();
        new ArrayList<>(active.keySet()).forEach(this::despawn);
        bond.close();
        // Companions are never saved, but sweep any left by a crash in loaded worlds just in case.
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) if (isCompanion(entity)) entity.remove();
        }
    }

    // --- Helpers ----------------------------------------------------------------------------

    private AccountId account(Player player) {
        return plugin.sessions().account(player.getUniqueId()).orElse(null);
    }

    /** A particle that needs no extra data, by name; the fallback when the name is unknown on this server version. */
    static Particle particle(String name, Particle fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            Particle particle = Particle.valueOf(name.toUpperCase(Locale.ROOT));
            return particle.getDataType() == Void.class ? particle : fallback;
        } catch (IllegalArgumentException missing) {
            return fallback;
        }
    }

    /** Reads looks, free unlocks and bundle contents from config.yml over the website's defaults. */
    static CosmeticCatalog load(ConfigurationSection section, NerdSmpPlugin plugin) {
        CosmeticCatalog defaults = CosmeticCatalog.defaults();
        if (section == null) return defaults;
        List<CosmeticCatalog.Companion> companions = new ArrayList<>();
        for (CosmeticCatalog.Companion c : defaults.companions()) {
            ConfigurationSection s = section.getConfigurationSection("companions." + c.slug());
            companions.add(s == null ? c : new CosmeticCatalog.Companion(c.slug(), c.name(), c.species(),
                s.getString("model", c.model()).toLowerCase(Locale.ROOT), Math.clamp(s.getDouble("scale", c.scale()), 0.25D, 3.0D),
                s.getBoolean("flying", c.flying()), s.getString("particle", c.particle()), c.color(), s.getBoolean("free", c.free()), c.source()));
        }
        List<CosmeticCatalog.Trail> trails = new ArrayList<>();
        for (CosmeticCatalog.Trail t : defaults.trails()) {
            ConfigurationSection s = section.getConfigurationSection("trails." + t.slug());
            trails.add(s == null ? t : new CosmeticCatalog.Trail(t.slug(), t.name(), t.summary(), t.pattern(),
                s.getString("particle", t.particle()), t.color(), s.getBoolean("free", t.free())));
        }
        List<CosmeticCatalog.Bundle> bundles = new ArrayList<>();
        for (CosmeticCatalog.Bundle b : defaults.bundles()) {
            List<String> contents = section.isList("bundles." + b.slug()) ? section.getStringList("bundles." + b.slug()) : b.contents();
            bundles.add(new CosmeticCatalog.Bundle(b.slug(), b.name(), CosmeticCatalog.configuredBundleContents(
                b, contents.stream().map(p -> p.toLowerCase(Locale.ROOT)).toList())));
        }
        try {
            return new CosmeticCatalog(companions, trails, defaults.tiers(), bundles, defaults.emotes(),
                defaults.titles(), defaults.auras(), defaults.finishers());
        } catch (IllegalArgumentException invalid) {
            plugin.getLogger().warning("Ignoring cosmetics settings in config.yml: " + invalid.getMessage());
            return defaults;
        }
    }

    /**
     * Reads companion-models.yml from the plugin folder, copying the bundled one there first so staff can
     * reshape the sculptures. Falls back to the bundled models if the edited file has a mistake.
     */
    static Map<String, SculptureModel> loadModels(NerdSmpPlugin plugin) {
        Map<String, SculptureModel> builtIn;
        try (var reader = new java.io.InputStreamReader(plugin.getResource("companion-models.yml"), java.nio.charset.StandardCharsets.UTF_8)) {
            builtIn = SculptureModel.read(YamlConfiguration.loadConfiguration(reader));
        } catch (java.io.IOException unreadable) {
            throw new IllegalStateException("The built-in companion models can't be read", unreadable);
        }
        java.io.File file = new java.io.File(plugin.getDataFolder(), "companion-models.yml");
        if (!file.exists()) plugin.saveResource("companion-models.yml", false);
        Map<String, SculptureModel> custom;
        try {
            custom = SculptureModel.read(YamlConfiguration.loadConfiguration(file));
        } catch (IllegalArgumentException invalid) {
            plugin.getLogger().warning("companion-models.yml has a mistake (" + invalid.getMessage() + "); using the built-in companions.");
            return builtIn;
        }
        // A file copied out before a new companion was added won't have its model; use the built-in one.
        SculptureModel.Merge merged = SculptureModel.merge(builtIn, custom);
        if (!merged.added().isEmpty()) {
            plugin.getLogger().info("companion-models.yml has no model for " + String.join(", ", merged.added())
                + "; using the built-in one. Copy it into the file from the plugin jar to reshape it.");
        }
        return merged.models();
    }

    /** A rare drop: the companion's charm, which can be traded until someone binds it to their account. */
    public Optional<org.bukkit.inventory.ItemStack> charm(String slug) {
        return catalog.companion(slug).map(companion -> {
            var item = new org.bukkit.inventory.ItemStack(Material.ECHO_SHARD);
            var meta = item.getItemMeta();
            meta.itemName(Chat.render("<gold>" + companion.name() + " Charm</gold>"));
            meta.lore(List.of(
                Chat.render("<gray>" + companion.species() + "</gray>").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                Chat.render("<dark_gray>Something in it still wants to follow you.</dark_gray>"),
                net.kyori.adventure.text.Component.empty(),
                Chat.render("<yellow>Right-click</yellow> <gray>to bind this companion to you.</gray>").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "companion_charm"), PersistentDataType.STRING, companion.slug());
            item.setItemMeta(meta);
            return item;
        });
    }

    /** Binding a charm spends it and grants the companion, the same way the store does. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCharm(org.bukkit.event.player.PlayerInteractEvent event) {
        var held = event.getItem();
        if (held == null || held.getType() != Material.ECHO_SHARD || !held.hasItemMeta() || !event.getAction().isRightClick()) return;
        String slug = held.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "companion_charm"), PersistentDataType.STRING);
        if (slug == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        var companion = catalog.companion(slug).orElse(null);
        AccountId account = account(player);
        if (companion == null || account == null) return;
        if (ownership(player).companions().contains(slug)) {
            Chat.notice(player, "You already have " + Chat.escape(companion.name()) + ". This charm could still find someone else.");
            return;
        }
        var spent = held.asOne();
        held.subtract();
        plugin.entitlements().grant(account, companion.product(), "charm:" + UUID.randomUUID(), null).thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (result == EntitlementService.GrantResult.APPLIED) refresh(player);
            else {
                player.getInventory().addItem(spent);
                Chat.error(player, "The charm wouldn't bind. Try again in a moment.");
            }
        }));
    }

    /** The wardrobe icon for a companion: the main block of its sculpture. */
    public Material icon(CosmeticCatalog.Companion companion) {
        SculptureModel model = models.get(companion.model());
        Material material = model == null ? null : Material.matchMaterial(model.parts().getFirst().block());
        return material == null || !material.isItem() ? Material.ARMOR_STAND : material;
    }

    Set<UUID> activeOwners() { return Set.copyOf(active.keySet()); }
}
