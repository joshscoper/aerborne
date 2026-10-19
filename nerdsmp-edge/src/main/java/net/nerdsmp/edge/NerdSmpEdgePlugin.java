package net.nerdsmp.edge;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(
    id = "nerdsmp-edge",
    name = "NerdSMP Edge",
    version = "0.1.0-SNAPSHOT",
    description = "Authenticated Java-only edge integration for Aerborne",
    authors = {"NerdSMP"}
)
public final class NerdSmpEdgePlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();

    @Inject
    public NerdSmpEdgePlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        proxy.getCommandManager().register(
            proxy.getCommandManager().metaBuilder("nerdedge").plugin(this).build(),
            new StatusCommand()
        );
        logger.info("NerdSMP edge initialized. Data directory: {}", dataDirectory);
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!proxy.getConfiguration().isOnlineMode() || id.version() != 4 || id.variant() != 2) {
            event.getPlayer().disconnect(Component.text("This server requires authenticated Minecraft Java Edition."));
            return;
        }
        online.add(event.getPlayer().getUniqueId());
        logger.info("Authenticated edge session opened for {} ({})",
            event.getPlayer().getUsername(), event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        online.remove(event.getPlayer().getUniqueId());
    }

    private final class StatusCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            invocation.source().sendMessage(Component.text(
                "Aerborne edge is online with " + online.size() + " authenticated sessions."
            ));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("nerdsmp.edge.status");
        }
    }
}
