package net.nerdsmp.paper.campaign;

import net.nerdsmp.api.service.CampaignService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns campaign totals into permanent, server-wide feature unlocks. */
public final class ServerProgressionService implements Listener, AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final CampaignService campaigns;
    private final File file;
    private final String season, root;
    private final Map<Unlock, String> milestoneIds = new EnumMap<>(Unlock.class);
    private final Set<Unlock> unlocked = EnumSet.noneOf(Unlock.class);
    private BukkitTask refreshTask;

    public ServerProgressionService(NerdSmpPlugin plugin, CampaignService campaigns) {
        this.plugin = plugin;
        this.campaigns = campaigns;
        file = new File(plugin.getDataFolder(), "server-progression.yml");
        season = plugin.getConfig().getString("season.id", "season-1");
        root = "seasons." + season + ".unlocked";
        milestoneIds.put(Unlock.WORLD_EVENTS,
            plugin.getConfig().getString("campaign.progression.world-events", "first-ripple"));
        milestoneIds.put(Unlock.NETHER,
            plugin.getConfig().getString("campaign.progression.nether", "broken-seal"));
        milestoneIds.put(Unlock.END,
            plugin.getConfig().getString("campaign.progression.end", "rift-opened"));
        load();
        save(unlocked);
    }

    public void start() {
        refresh();
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 100L, 100L);
    }

    public void refresh() {
        campaigns.activeCampaign().thenAccept(active -> active.ifPresent(snapshot ->
            Bukkit.getScheduler().runTask(plugin, () -> apply(snapshot))));
    }

    public boolean unlocked(Unlock unlock) {
        return unlocked.contains(unlock);
    }

    public boolean worldEventsUnlocked() {
        return unlocked(Unlock.WORLD_EVENTS);
    }

    public List<MilestoneView> views(CampaignService.CampaignSnapshot campaign) {
        List<MilestoneView> views = new ArrayList<>();
        for (Unlock unlock : Unlock.values()) {
            String milestone = milestoneIds.get(unlock);
            long required = campaign.milestones().getOrDefault(milestone, Long.MAX_VALUE);
            views.add(new MilestoneView(unlock, milestone, required, unlocked(unlock)));
        }
        return List.copyOf(views);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null || event.getFrom().getWorld() == null
            || event.getPlayer().hasPermission("nerdsmp.progression.bypass")) {
            return;
        }
        World.Environment destination = event.getTo().getWorld().getEnvironment();
        if (destination == event.getFrom().getWorld().getEnvironment()) {
            return;
        }
        Unlock required = destination == World.Environment.NETHER ? Unlock.NETHER
            : destination == World.Environment.THE_END ? Unlock.END : null;
        if (required == null || unlocked(required)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendRichMessage("<red>That realm is still sealed.</red> <gray>Advance <white>"
            + milestoneIds.get(required).replace('-', ' ') + "</white> on the campaign board to unlock it.</gray>");
    }

    private void apply(CampaignService.CampaignSnapshot campaign) {
        Set<Unlock> next = EnumSet.noneOf(Unlock.class);
        next.addAll(unlocked);
        next.addAll(earnedUnlocks(campaign, milestoneIds));
        if (next.equals(unlocked)) return;
        save(next);
        for (Unlock unlock : earnedUnlocks(campaign, milestoneIds)) {
            if (!unlocked.add(unlock)) {
                continue;
            }
            Bukkit.broadcast(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                net.nerdsmp.paper.presentation.BrandPalette.title("SERVER MILESTONE") + " <white>"
                    + unlock.displayName + " is now unlocked.</white>"));
        }
    }

    static Set<Unlock> earnedUnlocks(
        CampaignService.CampaignSnapshot campaign,
        Map<Unlock, String> milestoneIds
    ) {
        Set<Unlock> earned = EnumSet.noneOf(Unlock.class);
        for (Unlock unlock : Unlock.values()) {
            Long target = campaign.milestones().get(milestoneIds.get(unlock));
            if (target != null && campaign.totalPoints() >= target) {
                earned.add(unlock);
            }
        }
        return Set.copyOf(earned);
    }

    private void load() {
        YamlConfiguration yaml = net.nerdsmp.paper.storage.SeasonalYaml.load(file, season, "unlocked");
        if (yaml.contains(root) && !yaml.isList(root)) throw new IllegalStateException("Invalid world unlock state");
        yaml.getStringList(root).forEach(raw -> {
            try {
                unlocked.add(Unlock.valueOf(raw));
            } catch (IllegalArgumentException ignored) {
                throw new IllegalStateException("Unknown world unlock: " + raw, ignored);
            }
        });
    }

    private void save(Set<Unlock> values) {
        YamlConfiguration yaml = net.nerdsmp.paper.storage.SeasonalYaml.load(file, season, "unlocked");
        yaml.set(root, values.stream().map(Enum::name).sorted().toList());
        try {
            AtomicYamlFiles.save(yaml, file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist server progression", exception);
        }
    }

    @Override
    public void close() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        save(unlocked);
    }

    public enum Unlock {
        WORLD_EVENTS("World Events", Material.ENDER_EYE,
            "Rifts and ambient frontier events begin appearing."),
        NETHER("The Nether", Material.OBSIDIAN,
            "Nether portals stabilize for every player."),
        END("The End", Material.END_PORTAL_FRAME,
            "The final realm opens for every player.");

        private final String displayName;
        private final Material material;
        private final String description;

        Unlock(String displayName, Material material, String description) {
            this.displayName = displayName;
            this.material = material;
            this.description = description;
        }

        public String displayName() {
            return displayName;
        }

        public Material material() {
            return material;
        }

        public String description() {
            return description;
        }
    }

    public record MilestoneView(Unlock unlock, String milestoneId, long requiredPoints, boolean unlocked) {
    }
}
