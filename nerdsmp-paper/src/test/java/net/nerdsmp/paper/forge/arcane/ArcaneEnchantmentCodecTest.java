package net.nerdsmp.paper.forge.arcane;

import net.nerdsmp.paper.forge.ArcaneEnchantment;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArcaneEnchantmentCodecTest {
    /** A verbatim copy of the pre-2.0 PaperItemService reader, kept as the compatibility oracle. */
    private static Map<ArcaneEnchantment, Integer> legacyDecode(String encoded) {
        if (encoded == null || encoded.isBlank()) return Map.of();
        Map<ArcaneEnchantment, Integer> result = new EnumMap<>(ArcaneEnchantment.class);
        for (String part : encoded.split("\\|")) {
            String[] fields = part.split(":", 2);
            if (fields.length != 2) continue;
            ArcaneEnchantment enchantment = ArcaneEnchantment.fromId(fields[0]);
            try {
                int level = Integer.parseInt(fields[1]);
                if (enchantment != null && level >= 1 && level <= enchantment.maximumLevel()) result.put(enchantment, level);
            } catch (NumberFormatException ignored) {
                // The legacy reader skipped malformed entries.
            }
        }
        return Map.copyOf(result);
    }

    /** A verbatim copy of the pre-2.0 writer. */
    private static String legacyEncode(Map<ArcaneEnchantment, Integer> enchantments) {
        return enchantments.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey().id() + ":" + entry.getValue())
            .reduce((left, right) -> left + "|" + right).orElse("");
    }

    @Test
    void knownEntriesDecodeExactlyAsTheLegacyReaderForRandomInput() {
        SplittableRandom random = new SplittableRandom(7);
        String[] tokens = {"keen_edge", "warding", "scholar", "soulsiphon", "stormwake", "KEEN_EDGE", "", "x", "venombound"};
        String[] levels = {"1", "3", "5", "6", "0", "-2", "abc", "", "2"};
        for (int run = 0; run < 5_000; run++) {
            StringBuilder raw = new StringBuilder();
            int parts = random.nextInt(6);
            for (int part = 0; part < parts; part++) {
                if (part > 0) raw.append(random.nextInt(10) == 0 ? "||" : "|");
                raw.append(tokens[random.nextInt(tokens.length)]);
                if (random.nextInt(8) != 0) raw.append(':').append(levels[random.nextInt(levels.length)]);
            }
            String encoded = raw.toString();
            assertEquals(legacyDecode(encoded), ArcaneEnchantmentCodec.decode(encoded).known(), encoded);
        }
    }

    @Test
    void itemsWithOnlyKnownEntriesReEncodeByteForByte() {
        Map<ArcaneEnchantment, Integer> current = Map.of(ArcaneEnchantment.WARDING, 4,
            ArcaneEnchantment.KEEN_EDGE, 2, ArcaneEnchantment.VENOMBOUND, 5);
        String legacy = legacyEncode(current);
        assertEquals("keen_edge:2|warding:4|venombound:5", legacy);
        assertEquals(legacy, ArcaneEnchantmentCodec.encode(current, List.of()));
        assertEquals(legacy, ArcaneEnchantmentCodec.encode(ArcaneEnchantmentCodec.decode(legacy).known(),
            ArcaneEnchantmentCodec.decode(legacy).preserved()));
    }

    @Test
    void unknownAndMalformedEntriesSurviveAnUpgrade() {
        String stored = "keen_edge:2|stormwake:3|warding:abc|scholar:9|garbage";
        var decoded = ArcaneEnchantmentCodec.decode(stored);
        assertEquals(Map.of(ArcaneEnchantment.KEEN_EDGE, 2), decoded.known());
        assertEquals(List.of("stormwake:3", "warding:abc", "scholar:9", "garbage"), decoded.preserved());

        Map<ArcaneEnchantment, Integer> upgraded = new EnumMap<>(decoded.known());
        upgraded.put(ArcaneEnchantment.KEEN_EDGE, 4);
        String written = ArcaneEnchantmentCodec.encode(upgraded, decoded.preserved());
        assertEquals("keen_edge:4|stormwake:3|warding:abc|scholar:9|garbage", written);
        var reread = ArcaneEnchantmentCodec.decode(written);
        assertEquals(Map.of(ArcaneEnchantment.KEEN_EDGE, 4), reread.known());
        assertEquals(decoded.preserved(), reread.preserved());
    }

    @Test
    void preservedEntriesCanNeverOverrideAKnownLevel() {
        // A preserved out-of-range level is written after the valid one and stays invalid on re-read.
        String written = ArcaneEnchantmentCodec.encode(Map.of(ArcaneEnchantment.SCHOLAR, 3), List.of("scholar:9"));
        assertEquals(3, ArcaneEnchantmentCodec.decode(written).known().get(ArcaneEnchantment.SCHOLAR));
    }

    @Test
    void separatorsCannotBeSmuggledThroughPreservedEntries() {
        String written = ArcaneEnchantmentCodec.encode(Map.of(), List.of("a|keen_edge:5", "ok:1"));
        assertEquals("ok:1", written);
        assertTrue(ArcaneEnchantmentCodec.decode(written).known().isEmpty());
    }

    @Test
    void emptyAndBlankInputDecodeToNothing() {
        assertTrue(ArcaneEnchantmentCodec.decode(null).known().isEmpty());
        assertTrue(ArcaneEnchantmentCodec.decode("  ").preserved().isEmpty());
        assertEquals("", ArcaneEnchantmentCodec.encode(Map.of(), List.of()));
    }
}
