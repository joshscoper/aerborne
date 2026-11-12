package net.nerdsmp.paper.cosmetic;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.naming.Nicknames;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.presentation.ClientFeeds;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Companion bonds: the longer a companion keeps its owner company, the closer they get, and each bond level unlocks
 * something to look at. A name over its head, tricks, sparkles, a glow. Never stats, drops or XP: companions are sold in
 * the store, so a bond only ever changes how a companion looks and behaves.
 */
public final class CompanionBond {
    /** Minutes in company for each bond level, I to X. */
    static final int[] LEVELS = {0, 30, 90, 180, 360, 600, 900, 1350, 1800, 2400};

    /** What each bond level unlocks. Only looks. */
    public enum Unlock {
        NAME(2, "A name over its head", "/companion name"),
        SPIN(3, "Trick: Spin", "/companion trick spin"),
        SPARKLES(4, "Sparkles that follow it", "always on"),
        HOP(6, "Trick: Hop", "/companion trick hop"),
        TWIRL(8, "Trick: Twirl", "/companion trick twirl"),
        RADIANT(10, "A radiant glow", "always on");

        final int level; final String title, how;
        Unlock(int level, String title, String how) { this.level = level; this.title = title; this.how = how; }
        public int level() { return level; }
        public String title() { return title; }
    }

    public static int level(long minutes) {
        int level = 0;
        for (int need : LEVELS) if (minutes >= need) level++;
        return level;
    }

    public static boolean unlocked(long minutes, Unlock unlock) { return level(minutes) >= unlock.level; }

    private final NerdSmpPlugin plugin;
    private final Map<UUID, TextDisplay> nameplates = new HashMap<>();
    private final Map<UUID, Trick> tricks = new HashMap<>();
    private final Map<UUID, Long> lastCredit = new HashMap<>();
    private final Map<UUID, Location> lastPlace = new HashMap<>();
    private final Map<UUID, Long> lastMoved = new HashMap<>();

    private record Trick(Unlock kind, long until) { }

    CompanionBond(NerdSmpPlugin plugin) { this.plugin = plugin; }

    private AccountId account(Player player) { return plugin.sessions().account(player.getUniqueId()).orElse(null); }

    public long minutes(AccountId account, String slug) { return plugin.playerData().read(account, yaml -> yaml.getLong("companions.bond." + slug, 0L)); }
    String name(AccountId account, String slug) { return plugin.playerData().read(account, yaml -> yaml.getString("companions.name." + slug, "")); }
    String style(AccountId account, String slug) { return plugin.playerData().read(account, yaml -> yaml.getString("companions.name-style." + slug, "white")); }

    // ------------------------------------------------------------------ hooks from the companion's step

    /** Bends the companion's spot for a trick in progress: a spin turns it, a hop lifts it. */
    Location pose(Player owner, Location to, long tick) {
        Trick trick = tricks.get(owner.getUniqueId());
        if (trick == null) return to;
        long left = trick.until() - System.currentTimeMillis();
        if (left <= 0) { tricks.remove(owner.getUniqueId()); return to; }
        double t = 1 - left / 1200.0;
        Location posed = to.clone();
        switch (trick.kind()) {
            case SPIN -> posed.setYaw((float) (to.getYaw() + t * 720));
            case HOP -> posed.add(0, Math.sin(Math.PI * Math.min(1, t * 1.5)) * 0.9, 0);
            case TWIRL -> { posed.setYaw((float) (to.getYaw() + t * 1080)); posed.add(0, Math.sin(Math.PI * t) * 0.6, 0); }
            default -> { }
        }
        return posed;
    }

    /** After the companion moves: bond time, its name, sparkles and glow. */
    void decorate(Player owner, CosmeticCatalog.Companion companion, List<BlockDisplay> parts, Location at, List<Player> viewers, long tick) {
        AccountId account = account(owner);
        if (account == null || parts.isEmpty()) return;
        credit(owner, account, companion);
        long minutes = minutes(account, companion.slug());
        String name = name(account, companion.slug());
        TextDisplay plate = nameplates.get(owner.getUniqueId());
        if (unlocked(minutes, Unlock.NAME) && !name.isBlank()) {
            Location above = at.clone().add(0, 1.0 * Math.max(0.5, companion.scale()) + 0.35, 0);
            if (plate == null || !plate.isValid() || !plate.getWorld().equals(at.getWorld())) {
                if (plate != null) plate.remove();
                plate = at.getWorld().spawn(above, TextDisplay.class, display -> {
                    display.setPersistent(false);
                    display.setBillboard(Display.Billboard.CENTER);
                    display.setShadowed(true);
                    display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                    display.setTeleportDuration(2);
                    display.setViewRange(0.5F);
                    for (Player viewer : Bukkit.getOnlinePlayers()) if (viewer != owner && !viewers.contains(viewer)) viewer.hideEntity(plugin, display);
                });
                nameplates.put(owner.getUniqueId(), plate);
            }
            plate.text(Chat.render(Nicknames.Style.of(style(account, companion.slug())).paint(name, "<white>")));
            plate.teleport(above);
        } else if (plate != null) { plate.remove(); nameplates.remove(owner.getUniqueId()); }
        if (unlocked(minutes, Unlock.SPARKLES) && tick % 16 == 0) {
            Location spot = at.clone().add(0, 0.6 * Math.max(0.5, companion.scale()), 0);
            for (Player viewer : viewers) viewer.spawnParticle(Particle.WAX_ON, spot, 2, 0.35, 0.3, 0.35, 0);
        }
        boolean radiant = unlocked(minutes, Unlock.RADIANT);
        BlockDisplay first = parts.getFirst();
        if (first.isGlowing() != radiant) for (BlockDisplay part : parts) {
            part.setGlowing(radiant);
            if (radiant) part.setGlowColorOverride(Color.fromRGB(0xFFE08A));
        }
    }

    /** One minute of bond for every minute the owner has been about with the companion out (not standing idle). */
    private void credit(Player owner, AccountId account, CosmeticCatalog.Companion companion) {
        UUID id = owner.getUniqueId();
        long now = System.currentTimeMillis();
        Location here = owner.getLocation(), before = lastPlace.put(id, here);
        if (before == null || !before.getWorld().equals(here.getWorld()) || before.distanceSquared(here) > 0.01) lastMoved.put(id, now);
        long last = lastCredit.getOrDefault(id, now);
        if (!lastCredit.containsKey(id)) { lastCredit.put(id, now); return; }
        if (now - last < 60_000L) return;
        lastCredit.put(id, now);
        if (now - lastMoved.getOrDefault(id, 0L) > 180_000L) return; // AFK: no bond
        String key = "companions.bond." + companion.slug();
        long[] after = {0};
        plugin.playerData().update(account, yaml -> { after[0] = yaml.getLong(key, 0L) + 1; yaml.set(key, after[0]); });
        int level = level(after[0]);
        if (level > level(after[0] - 1)) levelled(owner, companion, level);
    }

    private void levelled(Player owner, CosmeticCatalog.Companion companion, int level) {
        StringBuilder unlocks = new StringBuilder();
        for (Unlock unlock : Unlock.values()) if (unlock.level == level) unlocks.append(" <aqua>Unlocked: ").append(unlock.title).append("</aqua>");
        Chat.send(owner, "<light_purple>♥ " + Chat.escape(companion.name()) + "'s bond is now level " + level + "!</light_purple>" + unlocks);
        ClientFeeds.toast(owner, "level", Chat.render("<light_purple>Companion Bond " + level + "</light_purple>"), Chat.render("<white>" + Chat.escape(companion.name()) + "</white>"));
        owner.playSound(owner.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.8F, 1.2F);
    }

    void despawn(UUID owner) {
        TextDisplay plate = nameplates.remove(owner);
        if (plate != null && plate.isValid()) plate.remove();
        tricks.remove(owner);
    }

    void forget(UUID owner) {
        despawn(owner);
        lastCredit.remove(owner);
        lastPlace.remove(owner);
        lastMoved.remove(owner);
    }

    void close() { new ArrayList<>(nameplates.keySet()).forEach(this::despawn); }

    // ------------------------------------------------------------------ commands

    /** /companion bond | name | trick &lt;spin|hop|twirl&gt;. Returns false when the arguments aren't a bond command. */
    public boolean command(Player player, String[] args, CosmeticService cosmetics) {
        String action = args[0].toLowerCase(Locale.ROOT);
        if (!List.of("bond", "name", "trick").contains(action)) return false;
        AccountId account = account(player);
        String slug = cosmetics.loadout(player).companion();
        var companion = cosmetics.catalog().companion(slug);
        if (account == null || companion.isEmpty() || slug.isBlank()) { Chat.error(player, "Summon a companion first: /companion"); return true; }
        long minutes = minutes(account, slug);
        switch (action) {
            case "bond" -> {
                int level = level(minutes);
                Chat.send(player, "<light_purple>♥ " + Chat.escape(companion.get().name()) + "</light_purple> <gray>bond level</gray> <white>" + level + "/10</white>"
                    + (level < LEVELS.length ? " <gray>(" + (LEVELS[level] - minutes) + " more minutes together)</gray>" : ""));
                for (Unlock unlock : Unlock.values())
                    Chat.send(player, (level >= unlock.level ? "  <green>✔ " : "  <dark_gray>✘ ") + "Level " + unlock.level + ": " + unlock.title
                        + (level >= unlock.level ? "</green> <gray>" + unlock.how + "</gray>" : "</dark_gray>"));
                Chat.send(player, "<dark_gray>Bonds only change looks: companions never give stats.</dark_gray>");
            }
            case "name" -> {
                if (!unlocked(minutes, Unlock.NAME)) { Chat.error(player, "Naming unlocks at bond level " + Unlock.NAME.level + ". /companion bond"); return true; }
                askName(player, account, slug, companion.get().name());
            }
            case "trick" -> {
                Unlock trick = args.length > 1 ? switch (args[1].toLowerCase(Locale.ROOT)) { case "spin" -> Unlock.SPIN; case "hop" -> Unlock.HOP; case "twirl" -> Unlock.TWIRL; default -> null; } : null;
                if (trick == null) { Chat.usage(player, "/companion trick <spin|hop|twirl>"); return true; }
                if (!unlocked(minutes, trick)) { Chat.error(player, trick.title + " unlocks at bond level " + trick.level + "."); return true; }
                tricks.put(player.getUniqueId(), new Trick(trick, System.currentTimeMillis() + 1200));
                player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_ITEM_THROWN, 0.8F, 1.3F);
            }
            default -> { }
        }
        return true;
    }

    private void askName(Player player, AccountId account, String slug, String species) {
        List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
        String current = style(account, slug);
        for (Nicknames.Style style : Nicknames.Style.values()) {
            if (style == Nicknames.Style.RARITY) continue;
            options.add(SingleOptionDialogInput.OptionEntry.create(style.name().toLowerCase(Locale.ROOT), Chat.render(style.paint(style.name().charAt(0) + style.name().substring(1).toLowerCase(Locale.ROOT), "<gray>")),
                style.name().equalsIgnoreCase(current)));
        }
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build();
        Dialog dialog = Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(Chat.render("<light_purple>Name your " + Chat.escape(species) + "</light_purple>")).canCloseWithEscape(true)
                .body(List.of(DialogBody.plainMessage(Chat.render("<gray>Free, and you can change it any time. Leave it empty to clear it.</gray>"))))
                .inputs(List.of(
                    DialogInput.text("name", Chat.render("<gray>Name</gray>")).maxLength(Nicknames.MAX_LENGTH).initial(name(account, slug)).width(240).build(),
                    DialogInput.singleOption("style", Chat.render("<gray>Look</gray>"), options).width(240).build()))
                .build())
            .type(DialogType.confirmation(
                ActionButton.create(Chat.render("<green>Save</green>"), null, 110, DialogAction.customClick((view, audience) -> {
                    String raw = view.getText("name"), style = view.getText("style");
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String clean = Nicknames.clean(raw);
                        if (clean == null) { Chat.error(player, "Names can be up to " + Nicknames.MAX_LENGTH + " letters, numbers, spaces and simple punctuation."); return; }
                        plugin.playerData().update(account, yaml -> {
                            yaml.set("companions.name." + slug, clean.isEmpty() ? null : clean);
                            yaml.set("companions.name-style." + slug, style == null ? "white" : style);
                        });
                        if (clean.isEmpty()) Chat.info(player, "The name is gone.");
                        else Chat.send(player, "<light_purple>♥ Your companion is now</light_purple> " + Nicknames.Style.of(style == null ? "white" : style).paint(clean, "<white>") + "<light_purple>.</light_purple>");
                    });
                }, once)),
                ActionButton.create(Chat.render("<gray>Cancel</gray>"), null, 110, null))));
        player.showDialog(dialog);
    }
}
