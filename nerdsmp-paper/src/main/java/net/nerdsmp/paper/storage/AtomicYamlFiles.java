package net.nerdsmp.paper.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class AtomicYamlFiles {
    private AtomicYamlFiles() {
    }

    public static void save(YamlConfiguration configuration, File target) throws IOException {
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        File temporary = new File(parent, target.getName() + ".tmp");
        Files.writeString(temporary.toPath(), configuration.saveToString(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
