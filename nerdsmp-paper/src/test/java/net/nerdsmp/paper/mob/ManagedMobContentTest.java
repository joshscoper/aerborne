package net.nerdsmp.paper.mob;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class ManagedMobContentTest {
    @Test void shippedManagedMobsDeclareTheirRankAndOrdinaryTutorialFoesAreNormal() throws Exception {
        for (String file : new String[]{"world-mobs", "expedition-mobs"}) {
            try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/content/" + file + ".yml")), StandardCharsets.UTF_8)) {
                var mobs = YamlConfiguration.loadConfiguration(reader).getConfigurationSection("mobs");
                assertNotNull(mobs);
                for (String id : mobs.getKeys(false)) assertTrue(java.util.Set.of("normal", "elite").contains(mobs.getString(id + ".rank")), id);
                if (file.equals("world-mobs")) {
                    assertEquals("normal", mobs.getString("field_gnawer.rank"));
                    assertEquals("normal", mobs.getString("straw_scarecrow.rank"));
                    assertEquals("elite", mobs.getString("gilded_overseer.rank"));
                    assertEquals("elite", mobs.getString("arena_champion.rank"));
                } else {
                    assertEquals("normal", mobs.getString("skyway_brigand.rank"));
                    assertEquals("normal", mobs.getString("cloudline_archer.rank"));
                    assertEquals("elite", mobs.getString("gale_raider.rank"));
                    assertTrue(mobs.getBoolean("old_sentinel.boss"));
                }
            }
        }
    }
}
