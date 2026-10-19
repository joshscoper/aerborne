package net.nerdsmp.paper.anticheat.packet;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Watchdog's own packet layer. Inserts a read-only handler into each player's Netty pipeline just before the
 * server's packet handler, so it sees every decoded serverbound packet in arrival order. It never changes, drops or
 * delays packets: decoding failures are counted and the packet always continues to the server.
 */
public final class PacketTap implements Listener, AutoCloseable {
    public static final String HANDLER = "nerdsmp_watchdog";
    private static final String TARGET = "packet_handler";

    /** Receives packets on the player's Netty thread. Calls for one player are always serial and in order. */
    public interface Sink {
        void receive(UUID playerId, ClientPacket packet);
    }

    private final Plugin plugin;
    private final Sink sink;
    private PacketDecoder decoder;
    private final AtomicLong decodeErrors = new AtomicLong();
    private volatile String status = "Not started";

    public PacketTap(Plugin plugin, Sink sink) {
        this.plugin = plugin;
        this.sink = sink;
    }

    /** Resolves packet classes against the running server. Returns false and stays inert if this server differs. */
    public boolean start() {
        try {
            decoder = PacketDecoder.resolve(Bukkit.getServer().getClass().getClassLoader());
            status = "Ready";
            for (Player player : Bukkit.getOnlinePlayers()) inject(player);
            plugin.getLogger().info("Watchdog packet layer ready.");
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            status = "Unavailable on this server version: " + failure.getClass().getSimpleName();
            plugin.getLogger().warning("Watchdog packet layer unavailable (" + failure + "). Packet checks are off; event checks still run.");
            return false;
        }
    }

    public boolean ready() {
        return decoder != null;
    }

    public String status() {
        return status;
    }

    public long decodeErrors() {
        return decodeErrors.get();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (decoder != null) inject(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer());
    }

    @Override
    public void close() {
        for (Player player : Bukkit.getOnlinePlayers()) remove(player);
    }

    private void inject(Player player) {
        Channel channel = channel(player);
        if (channel == null) return;
        UUID id = player.getUniqueId();
        channel.eventLoop().execute(() -> {
            var pipeline = channel.pipeline();
            if (pipeline.get(HANDLER) == null && pipeline.get(TARGET) != null) pipeline.addBefore(TARGET, HANDLER, new Handler(id));
        });
    }

    private void remove(Player player) {
        Channel channel = channel(player);
        if (channel == null || !channel.isOpen()) return;
        channel.eventLoop().execute(() -> { if (channel.pipeline().get(HANDLER) != null) channel.pipeline().remove(HANDLER); });
    }

    private Channel channel(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object listener = field(handle, "connection");
            Object connection = field(listener, "connection");
            return (Channel) field(connection, "channel");
        } catch (ReflectiveOperationException | RuntimeException failure) {
            plugin.getLogger().log(Level.FINE, "No packet channel for " + player.getName(), failure);
            return null;
        }
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored) {
                // Declared on a superclass.
            }
        }
        throw new NoSuchFieldException(name);
    }

    final class Handler extends ChannelInboundHandlerAdapter {
        private final UUID playerId;

        Handler(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object message) {
            try {
                ClientPacket packet = decoder.decode(message, System.nanoTime());
                if (packet != null) sink.receive(playerId, packet);
            } catch (Throwable failure) {
                if (decodeErrors.getAndIncrement() == 0 && plugin != null) plugin.getLogger().log(Level.WARNING, "Watchdog could not read a packet; continuing", failure);
            } finally {
                context.fireChannelRead(message);
            }
        }
    }

    /** Test hook: a handler that feeds the given sink with an explicit decoder. */
    static ChannelInboundHandlerAdapter testHandler(PacketDecoder decoder, Sink sink, UUID playerId) {
        PacketTap tap = new PacketTap(null, sink);
        tap.decoder = decoder;
        return tap.new Handler(playerId);
    }
}
