package net.nerdsmp.paper.vote;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoteMenuTest {
    private static VoteService.Site site(String name, int slot) {
        return new VoteService.Site(name, "https://example.com/" + name, name, "paper", slot, "");
    }

    private static VoteService.Settings read(String sites) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("sites:\n" + sites);
        return VoteService.Settings.read(yaml);
    }

    @Test
    void afewSitesLineUpInTheMiddleOfTheMenu() {
        List<VoteService.Site> sites = List.of(site("a", -1), site("b", -1), site("c", -1));
        Map<VoteService.Site, Integer> layout = VoteMenu.layout(sites);
        assertEquals(21, layout.get(sites.get(0)));
        assertEquals(22, layout.get(sites.get(1)));
        assertEquals(23, layout.get(sites.get(2)));
    }

    @Test
    void aChosenSlotIsKeptAndTheRestFillAroundIt() {
        List<VoteService.Site> sites = List.of(site("a", -1), site("b", 22), site("c", -1));
        Map<VoteService.Site, Integer> layout = VoteMenu.layout(sites);
        assertEquals(22, layout.get(sites.get(1)));
        assertEquals(2, layout.values().stream().filter(slot -> slot != 22).distinct().count());
        assertTrue(layout.values().stream().allMatch(VoteMenu::siteSlot));
    }

    @Test
    void manySitesFillEveryRow() {
        List<VoteService.Site> sites = java.util.stream.IntStream.range(0, VoteMenu.SITE_SLOTS.length).mapToObj(i -> site("s" + i, -1)).toList();
        assertEquals(VoteMenu.SITE_SLOTS.length, VoteMenu.layout(sites).values().stream().distinct().count());
    }

    @Test
    void sitesReadWithTheirIconSlotAndDescription() throws Exception {
        VoteService.Settings settings = read("""
              - name: Planet Minecraft
                url: https://www.planetminecraft.com/server/aerborne/vote/
                service: PlanetMinecraft.com
                icon: GRASS_BLOCK
                slot: 20
                description: The biggest Minecraft community site.
              - name: TopG
                url: https://topg.org/minecraft-servers/server-1
            """);
        assertEquals(List.of(
            new VoteService.Site("Planet Minecraft", "https://www.planetminecraft.com/server/aerborne/vote/", "PlanetMinecraft.com", "grass_block", 20,
                "The biggest Minecraft community site."),
            new VoteService.Site("TopG", "https://topg.org/minecraft-servers/server-1", "TopG", "paper", -1, "")), settings.sites());
        assertEquals("<gold><bold>Vote for Aerborne</bold></gold>", settings.title());
    }

    @Test
    void badSitesAreRefusedWithAReason() {
        assertThrows(IllegalArgumentException.class, () -> read("  - name: Plain\n    url: http://example.com/vote\n"));
        assertThrows(IllegalArgumentException.class, () -> read("  - url: https://example.com/vote\n"));
        assertThrows(IllegalArgumentException.class, () -> read("  - name: Edge\n    url: https://example.com/vote\n    slot: 9\n"));
        assertThrows(IllegalArgumentException.class, () -> read("""
              - name: A
                url: https://example.com/a
                slot: 20
              - name: B
                url: https://example.com/b
                slot: 20
            """));
    }
}
