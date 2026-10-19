package net.nerdsmp.paper.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NpcContentContractTest {
    @Test
    void betaNpcRosterShipsFactionsDialoguesConsequencesAndShops() throws IOException {
        String content = resource("/content/npcs.yml");
        String plugin = resource("/plugin.yml");

        assertTrue(content.startsWith("schema-version: 10"));
        assertEquals(3L, Pattern.compile("(?m)^  (frontier_wardens|rift_scholars|free_delvers):$")
            .matcher(content).results().count());
        assertEquals(31L, Pattern.compile("(?m)^    root-dialogue: [a-z0-9_]+$")
            .matcher(content).results().count());
        for (String npc : java.util.List.of(
            "cassia_dawn", "aeron_vale", "pell_wren", "veyra_loom", "cato_flint", "arlen_voss",
            "kestrel_way", "nima_hart", "dorian_ash", "vera_lune", "rook_salen", "fen_calder",
            "lyra_senn", "garrick_mott"
        )) {
            assertTrue(content.contains("  " + npc + ":"), "Missing civic NPC " + npc);
        }
        assertTrue(content.contains("type: reputation"));
        assertTrue(content.contains("type: marks"));
        assertTrue(content.contains("type: flag"));
        assertTrue(content.contains("type: open_shop"));
        assertTrue(content.contains("type: open_identifier"));
        assertTrue(content.contains("type: open_bank"));
        assertTrue(content.contains("type: open_repair"));
        assertTrue(content.contains("type: open_contracts"));
        assertTrue(content.contains("type: open_archive"));
        assertTrue(content.contains("type: open_fishery"));
        assertTrue(content.contains("type: open_alchemy"));
        assertTrue(content.contains("type: open_forge"));
        assertTrue(content.contains("type: open_enchantments"));
        assertTrue(content.contains("type: open_command"));
        assertEquals(226L, Pattern.compile("(?m)^        buy-price: [0-9]+$")
            .matcher(content).results().count());
        assertEquals(226L, Pattern.compile("(?m)^        sell-price: [0-9]+$")
            .matcher(content).results().count());
        assertEquals(226L, Pattern.compile("(?m)^        daily-sell-limit: [0-9]+$")
            .matcher(content).results().count());
        assertEquals(226L, Pattern.compile("(?m)^        material: [A-Z0-9_]+$")
            .matcher(content).results().count());
        assertEquals(0L, Pattern.compile("(?m)^        custom-item: [a-z0-9_]+$")
            .matcher(content).results().count());
        for (String shop : java.util.List.of("warden_supplies", "scholar_supplies", "delver_supplies")) {
            assertTrue(Pattern.compile("(?ms)^  " + shop + ":\n    title: .+\n    description: .+$")
                .matcher(content).find(), "Shop lacks a clear purpose: " + shop);
        }
        for (String branch : java.util.List.of("mara_stores", "elian_supplies", "bram_packs")) {
            assertTrue(content.contains("  " + branch + ":"), "Missing merchant dialogue branch " + branch);
        }
        for (String command : java.util.List.of(
            "menu", "quickstart", "settings", "profile", "adventurer", "skills", "mastery", "jobs",
            "daily", "playtime", "campaign", "warps", "party", "social", "duel", "cosmetics",
            "relics", "recipes", "fishing", "excavation", "claim",
            "market", "grave"
        )) {
            assertTrue(content.contains("command: " + command), "NPC roster does not expose /" + command);
        }
        assertTrue(content.contains("shop-price-multiplier: 0.80"));
        assertTrue(content.contains("can-trade: false"));
        assertTrue(plugin.contains("  npc:"));
        assertTrue(plugin.contains("  reputation:"));
        assertTrue(plugin.contains("nerdsmp.npc.admin:"));
        assertTrue(plugin.contains("nerdsmp.station.remote:"));
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = Objects.requireNonNull(getClass().getResourceAsStream(path), path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
