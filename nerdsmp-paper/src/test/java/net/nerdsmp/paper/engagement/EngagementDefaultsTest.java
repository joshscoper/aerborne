package net.nerdsmp.paper.engagement;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EngagementDefaultsTest {
    private YamlConfiguration config() throws Exception {
        var defaults=new YamlConfiguration();defaults.loadFromString("engagement:\n  playtime-rewards:\n    '30': 75\n    '120': 175\n");
        var config=new YamlConfiguration();config.setDefaults(defaults);return config;
    }
    @Test void absentMilestoneSectionUsesRealDefaultKeysWithoutWritingThem() throws Exception {
        var config=config();String before=config.saveToString();
        assertEquals(Map.of(30,75L,120,175L),EngagementService.readPlaytimeRewards(config));
        assertEquals(before,config.saveToString());assertFalse(config.isSet("engagement.playtime-rewards"));
    }
    @Test void explicitOperatorSchedulesAreNotMergedWithBundledMilestones() throws Exception {
        var config=config();config.set("engagement.playtime-rewards.45",123L);
        assertEquals(Map.of(45,123L),EngagementService.readPlaytimeRewards(config));
    }
    @Test void explicitEmptyMalformedOrInvalidSchedulesStillFailClosed() throws Exception {
        var config=config();config.createSection("engagement.playtime-rewards");
        assertThrows(IllegalStateException.class,()->EngagementService.readPlaytimeRewards(config));
        config.set("engagement.playtime-rewards", "not a schedule");
        assertThrows(IllegalStateException.class,()->EngagementService.readPlaytimeRewards(config));
        config.set("engagement.playtime-rewards",null);config.set("engagement.playtime-rewards.30",-1L);
        assertThrows(IllegalStateException.class,()->EngagementService.readPlaytimeRewards(config));
    }
}
