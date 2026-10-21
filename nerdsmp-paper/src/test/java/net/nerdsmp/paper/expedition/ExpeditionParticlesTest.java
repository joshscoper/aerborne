package net.nerdsmp.paper.expedition;

import org.bukkit.Particle;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Paper throws when a particle that needs data (dust colour, power) is spawned without it. */
class ExpeditionParticlesTest {
    @Test void everyParticleTheExpeditionCodeSpawnsNeedsNoData() throws IOException {
        Set<String> used = new TreeSet<>();
        var pattern = Pattern.compile("Particle\\.([A-Z_]+)");
        try (var files = Files.walk(Path.of("src/main/java/net/nerdsmp/paper/expedition"))) {
            // Telegraph is the one helper that spawns data particles, and it always passes the data.
            for (Path file : files.filter(path -> path.toString().endsWith(".java") && !path.endsWith("Telegraph.java")).toList()) {
                var matcher = pattern.matcher(Files.readString(file));
                while (matcher.find()) used.add(matcher.group(1));
            }
        }
        assertFalse(used.isEmpty());
        for (String name : used) assertEquals(Void.class, Particle.valueOf(name).getDataType(), name);
    }
}
