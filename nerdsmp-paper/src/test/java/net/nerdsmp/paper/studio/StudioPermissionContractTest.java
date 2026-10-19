package net.nerdsmp.paper.studio;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

class StudioPermissionContractTest {
    private YamlConfiguration metadata(){return YamlConfiguration.loadConfiguration(new File("src/main/resources/plugin.yml"));}
    @Test void teamBundlesNeverGrantPlayerModerationOrEconomicAdministration(){
        var yaml=metadata();
        for(String role:new String[]{"builder","designer","tester"}) {
            String root="permissions.nerdsmp.studio."+role;
            assertFalse(yaml.getBoolean(root+".default"));
            var children=yaml.getConfigurationSection(root+".children"); assertNotNull(children);
            assertTrue(children.getBoolean("nerdsmp.studio.use"));
            for(String key:children.getKeys(true).stream().filter(children::isBoolean).toList()) assertFalse(key.equals("nerdsmp.admin")||key.startsWith("nerdsmp.staff")
                ||key.startsWith("nerdsmp.store")||key.equals("nerdsmp.claim.bypass"),key);
        }
    }
    @Test void testerCannotMutateContentAndDesignersRetainDirectAuthoring(){
        var yaml=metadata(); var tester=yaml.getConfigurationSection("permissions.nerdsmp.studio.tester.children");
        assertFalse(tester.getBoolean("nerdsmp.content.items")); assertFalse(tester.getBoolean("nerdsmp.content.mobs"));
        assertFalse(tester.getBoolean("nerdsmp.loot.admin")); assertFalse(tester.getBoolean("nerdsmp.npc.admin"));
        var designer=yaml.getConfigurationSection("permissions.nerdsmp.studio.designer.children");
        assertTrue(designer.getBoolean("nerdsmp.content.items")); assertTrue(designer.getBoolean("nerdsmp.content.mobs"));
        assertTrue(designer.getBoolean("nerdsmp.studio.recipes"));
    }
    @Test void everyToolCommandHasDomainPermissionAndAliasesResolveToHub(){
        var yaml=metadata();
        for(String command:new String[]{"studio","lootstudio","worldstudio","npcstudio","recipestudio"})
            assertFalse(yaml.getString("commands."+command+".permission","").isBlank());
        assertTrue(yaml.getStringList("commands.studio.aliases").contains("content"));
    }
}
