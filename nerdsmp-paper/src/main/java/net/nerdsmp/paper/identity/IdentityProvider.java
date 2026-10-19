package net.nerdsmp.paper.identity;

import net.nerdsmp.api.model.PlatformIdentity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import net.kyori.adventure.text.Component;
import java.util.UUID;

/** Java-only identity. Authentication still requires online mode or a secured modern-forwarding proxy. */
public final class IdentityProvider implements Listener {
    public static boolean supported(UUID id) { return id != null && id.version() == 4 && id.variant() == 2; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(AsyncPlayerPreLoginEvent event) {
        if (!supported(event.getUniqueId())) event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
            Component.text("This server supports authenticated Minecraft Java Edition accounts only."));
    }

    public PlatformIdentity identity(Player player) {
        return new PlatformIdentity(PlatformIdentity.Platform.JAVA, player.getUniqueId().toString());
    }
}
