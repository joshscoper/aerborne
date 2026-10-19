package net.nerdsmp.paper.forge.arcane;

import net.nerdsmp.paper.forge.ArcaneEnchantment;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Lossless reader and writer for the {@code custom_enchantments} item string ({@code id:level|id:level}).
 *
 * <p>Known entries decode exactly as the original reader did: the last valid entry for an id wins.
 * Everything the reader could not use (an id from a newer build, a level outside today's range, a
 * malformed pair) is kept verbatim and written back after the known entries, so enchanting an item
 * never deletes data this build does not understand. Earlier duplicates of a known id never had an
 * effect and are not kept.
 */
public final class ArcaneEnchantmentCodec {
    private ArcaneEnchantmentCodec() {
    }

    public record Decoded(Map<ArcaneEnchantment, Integer> known, List<String> preserved) {
        public Decoded {
            known = Map.copyOf(known);
            preserved = List.copyOf(preserved);
        }

        public static final Decoded EMPTY = new Decoded(Map.of(), List.of());
    }

    public static Decoded decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return Decoded.EMPTY;
        Map<ArcaneEnchantment, Integer> known = new EnumMap<>(ArcaneEnchantment.class);
        List<String> preserved = new ArrayList<>();
        for (String part : encoded.split("\\|")) {
            if (part.isEmpty()) continue;
            String[] fields = part.split(":", 2);
            ArcaneEnchantment enchantment = fields.length == 2 ? ArcaneEnchantment.fromId(fields[0]) : null;
            Integer level = fields.length == 2 ? parse(fields[1]) : null;
            if (enchantment != null && level != null && level >= 1 && level <= enchantment.maximumLevel()) {
                known.put(enchantment, level);
            } else {
                preserved.add(part);
            }
        }
        return new Decoded(known, preserved);
    }

    /** Known entries in enum order (the original format), then preserved entries in their original order. */
    public static String encode(Map<ArcaneEnchantment, Integer> known, List<String> preserved) {
        List<String> parts = new ArrayList<>();
        known.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> parts.add(entry.getKey().id() + ":" + entry.getValue()));
        for (String entry : preserved) {
            if (entry != null && !entry.isEmpty() && !entry.contains("|")) parts.add(entry);
        }
        return String.join("|", parts);
    }

    private static Integer parse(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException malformed) {
            return null;
        }
    }
}
