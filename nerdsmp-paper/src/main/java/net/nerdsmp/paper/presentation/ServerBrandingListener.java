package net.nerdsmp.paper.presentation;

import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

/** Server-list pings require no account/database access and never fake population or version data. */
public final class ServerBrandingListener implements Listener {
    private final NerdSmpPlugin plugin;

    public ServerBrandingListener(NerdSmpPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPing(ServerListPingEvent event) {
        if (plugin.getConfig().getBoolean("branding.server-list-enabled", true)) event.motd(plugin.branding().motd());
    }
}
