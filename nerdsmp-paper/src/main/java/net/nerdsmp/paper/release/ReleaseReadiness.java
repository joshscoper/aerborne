package net.nerdsmp.paper.release;

import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.command.CommandSender;

/** Diagnostics are observations, never an automatic public-launch approval. */
public final class ReleaseReadiness {
    private ReleaseReadiness() { }
    public static void show(NerdSmpPlugin plugin, CommandSender sender) {
        sender.sendMessage(plugin.displayName() + " — release review (not a launch approval)");
        sender.sendMessage("Mode: " + (plugin.skyblockEnabled() ? "Skyblock" : "SMP") + " | season: " + plugin.getConfig().getString("season.id"));
        sender.sendMessage("Public display: " + plugin.branding().name() + " | " + plugin.branding().mode() + " | " + plugin.branding().tagline());
        sender.sendMessage("Server-list branding: " + (plugin.getConfig().getBoolean("branding.server-list-enabled", true) ? "enabled (proxy MOTD may override it)" : "disabled"));
        sender.sendMessage("Web API: " + (plugin.webApi() == null ? "disabled or unavailable — see security/web-api.yml and startup log"
            : plugin.webApi().status()));
        if (plugin.arcane() != null) {
            var arcane = plugin.arcane().settings();
            sender.sendMessage("Arcane progression: research " + arcane.research().enabled() + ", chosen imbuement " + arcane.imbuement().enabled()
                + ", attunement " + arcane.attunement().enabled() + ", corruption " + arcane.corruption().enabled()
                + ". Review existing configuration with /forgeadmin status; /forgeadmin rollout core previews the safe rollout.");
        }
        String storage = plugin.getConfig().getString("storage.mode", "yaml");
        sender.sendMessage("Persistence: " + (storage.equalsIgnoreCase("memory") ? "persistent per-player YAML (legacy memory alias)" : storage));
        sender.sendMessage("Authentication: " + (plugin.getServer().getOnlineMode() ? "authenticated Java" : "OFFLINE BACKEND — verify modern proxy forwarding and private binding"));
        if (!plugin.getServer().getOnlineMode() && (plugin.getServer().getIp().isBlank() || plugin.getServer().getIp().equals("0.0.0.0")))
            sender.sendMessage("REVIEW — offline backend binds all container/host interfaces; confirm no public backend port and authenticated proxy-only access.");
        if (plugin.getServer().getMaxPlayers() > 30) sender.sendMessage("REVIEW — slot count exceeds the initial 30-player planning envelope; load-test before expanding.");
        if (plugin.getConfig().getString("anti-cheat.mode", "autonomous").equalsIgnoreCase("autonomous"))
            sender.sendMessage("REVIEW — autonomous anti-cheat is enabled; require false-positive acceptance evidence before public enforcement.");
        sender.sendMessage("Player trade recovery holds: " + plugin.trade().pendingRecoveries() + " — details: /trade review");
        if (plugin.skyblockEnabled()) {
            sender.sendMessage(plugin.skyblock().automation().diagnostics());
            var missing = plugin.npcs().missingHubDefinitions();
            sender.sendMessage(missing.isEmpty() ? "Hub NPC roster: all definitions have a live city anchor."
                : "Hub NPCs needing manual placement: " + String.join(", ", missing));
            sender.sendMessage("Public hub: " + (plugin.travel().publicSpawn()==null ? "NOT SET — use /setspawn" : "configured")
                + " | Saved warps: " + plugin.travel().warpNames().size());
            sender.sendMessage("Zones: " + plugin.zones().all().size() + " | Active managed mobs: " + plugin.zoneSpawner().alive()
                + " — review routes, spawns and acquisition paths before opening.");
        }
        sender.sendMessage("REVIEW — /market review for unsettled payments or inventory deliveries.");
        sender.sendMessage("BLOCKED — campaign inventory delivery, bank, starter and grave crash boundaries still need hardening.");
        if (!plugin.skyblockEnabled()) sender.sendMessage("BLOCKED — legacy server order persistence and delivery require a rewrite.");
        sender.sendMessage("MANUAL — two-client trade/disconnect/crash tests; Java multiplayer journeys; proxy authentication; load and restore drills.");
        sender.sendMessage("MANUAL — permissions/store audit: remote stations must not become paid gameplay advantages.");
        sender.sendMessage("Checklist and recovery procedures: docs/HEARTHSKY_RELEASE.md");
    }
}
