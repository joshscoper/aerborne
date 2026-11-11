package net.aerborne.client.bridge;

import net.aerborne.client.protocol.Capabilities;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Advertises presentation features only; all gameplay stays in NerdSMP's command handlers. */
public final class AerborneClientBridge extends JavaPlugin implements Listener {
    private final byte[] enabled = new Capabilities(Capabilities.VERSION, Capabilities.MENU).encode();

    @Override public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, Capabilities.CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "aerborne:world");
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this,
                () -> getServer().getOnlinePlayers().forEach(this::advertise), 20L, 100L);
    }

    @EventHandler public void onChannel(PlayerRegisterChannelEvent event) {
        if (Capabilities.CHANNEL.equals(event.getChannel()) || "aerborne:world".equals(event.getChannel())) advertise(event.getPlayer());
    }

    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { advertise(event.getPlayer()); }

    private void advertise(Player player) {
        if (player.isOnline() && player.getListeningPluginChannels().contains(Capabilities.CHANNEL)) {
            player.sendPluginMessage(this, Capabilities.CHANNEL, enabled);
            if (player.getListeningPluginChannels().contains("aerborne:world")) {
                var world = player.getWorld().getUID();
                byte[] bytes = java.nio.ByteBuffer.allocate(16).putLong(world.getMostSignificantBits()).putLong(world.getLeastSignificantBits()).array();
                player.sendPluginMessage(this, "aerborne:world", bytes);
            }
        }
    }

    @Override public void onDisable() {
        byte[] disabled = new Capabilities(Capabilities.VERSION, 0).encode();
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getListeningPluginChannels().contains(Capabilities.CHANNEL)) {
                player.sendPluginMessage(this, Capabilities.CHANNEL, disabled);
            }
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }
}
