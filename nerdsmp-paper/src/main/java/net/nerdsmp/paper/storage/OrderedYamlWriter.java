package net.nerdsmp.paper.storage;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Writes one YAML file off the main thread without ever letting an older snapshot overwrite a newer
 * one. Callers serialize on the main thread (where live objects such as item stacks are safe to read)
 * and hand over the text; the disk work runs asynchronously, or inline once the plugin is disabling.
 */
public final class OrderedYamlWriter {
    private final Plugin plugin;
    private final File file;
    private final String label;
    private final Object lock = new Object();
    private final AtomicLong issued = new AtomicLong();
    private long written;

    public OrderedYamlWriter(Plugin plugin, File file, String label) {
        this.plugin = plugin;
        this.file = file;
        this.label = label;
    }

    /** Queues a write; returns at once while the plugin is running. */
    public void submit(String content) {
        long version = issued.incrementAndGet();
        if (plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(content, version));
        else write(content, version);
    }

    /** Writes now on the calling thread, for shutdown. */
    public void flush(String content) {
        write(content, issued.incrementAndGet());
    }

    /** True when {@code version} is not older than what has landed; exposed for tests. */
    boolean write(String content, long version) {
        synchronized (lock) {
            if (version <= written) return false; // A newer snapshot already landed.
            try {
                AtomicYamlFiles.write(content, file);
                written = version;
                return true;
            } catch (IOException failure) {
                plugin.getLogger().log(Level.WARNING, "Could not save " + label, failure);
                return false;
            }
        }
    }
}
