package net.nerdsmp.paper.storage;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OrderedYamlWriterTest {
    /** A disabled plugin, so submit() writes inline and the test needs no scheduler. */
    private static Plugin disabledPlugin() {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[] { Plugin.class }, (proxy, method, args) -> switch (method.getName()) {
            case "isEnabled" -> false;
            case "getLogger" -> Logger.getLogger("test");
            case "toString" -> "test plugin";
            default -> null;
        });
    }

    @Test void anOlderSnapshotNeverOverwritesANewerOne(@TempDir Path folder) throws Exception {
        File file = folder.resolve("data.yml").toFile();
        var writer = new OrderedYamlWriter(disabledPlugin(), file, "test data");
        assertTrue(writer.write("value: 2\n", 2));
        assertFalse(writer.write("value: 1\n", 1), "the late, older write is dropped");
        assertEquals("value: 2\n", Files.readString(file.toPath()));
    }

    @Test void writesLandInOrderAndLeaveNoTemporaryFile(@TempDir Path folder) throws Exception {
        File file = folder.resolve("nested/data.yml").toFile();
        var writer = new OrderedYamlWriter(disabledPlugin(), file, "test data");
        writer.submit("value: a\n");
        writer.flush("value: b\n");
        assertEquals("value: b\n", Files.readString(file.toPath()));
        assertFalse(new File(file.getParentFile(), "data.yml.tmp").exists());
    }
}
