package net.nerdsmp.paper.item;

import net.nerdsmp.paper.forge.GemType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class SocketCodecTest {
    @Test void everyGemAndTierRoundTripsThroughExistingFormat() {
        for (GemType type : GemType.values()) for (int tier = 1; tier <= 4; tier++) {
            var gem = new PaperItemService.SocketedGem(type, tier);
            assertEquals(type.id() + ":" + tier, SocketCodec.encode(List.of(gem)));
            var decoded = SocketCodec.decode(SocketCodec.encode(List.of(gem)), 1);
            assertTrue(decoded.valid());
            assertEquals(List.of(gem), decoded.sockets());
        }
    }

    @Test void duplicateGemsAndSocketOrderArePreserved() {
        var sockets = List.of(new PaperItemService.SocketedGem(GemType.RUBY, 2),
            new PaperItemService.SocketedGem(GemType.PRISMATIC, 4),
            new PaperItemService.SocketedGem(GemType.RUBY, 2));
        assertEquals(sockets, SocketCodec.decode(SocketCodec.encode(sockets), 3).sockets());
    }

    @Test void missingAndEmptyPayloadsMeanNoGems() {
        assertTrue(SocketCodec.decode(null, 1).valid());
        assertTrue(SocketCodec.decode("", 1).valid());
        assertTrue(SocketCodec.decode("", 0).sockets().isEmpty());
    }

    @Test void badEntryInvalidatesWholePayloadInsteadOfLaunderingRemainingGems() {
        for (String encoded : List.of("ruby:1|unknown:1", "ruby:1|ruby:0", "ruby:1|ruby:5",
            "ruby:1|ruby:-1", "ruby:1|ruby:01", "ruby:1|ruby:1.0", "ruby:1|ruby:x",
            "ruby:1|RUBY:1", "ruby:1|ruby:1:2", "ruby:1| ruby:1", " ")) {
            assertFalse(SocketCodec.decode(encoded, 4).valid(), encoded);
            assertTrue(SocketCodec.decode(encoded, 4).sockets().isEmpty(), encoded);
        }
    }

    @Test void EmptySeparatorsCannotEraseSocketEntries() {
        for (String encoded : List.of("|ruby:1", "ruby:1|", "ruby:1||ruby:2", ":1", "ruby:")) {
            assertFalse(SocketCodec.decode(encoded, 4).valid(), encoded);
        }
    }

    @Test void PayloadCannotExceedRealCapacityOrHardLimit() {
        assertFalse(SocketCodec.decode("ruby:1|ruby:2", 1).valid());
        assertFalse(SocketCodec.decode("ruby:1", 0).valid());
        assertFalse(SocketCodec.decode("", -1).valid());
        assertFalse(SocketCodec.decode("", 5).valid());
        assertFalse(SocketCodec.decode("ruby:1|".repeat(100), 4).valid());
        assertThrows(IllegalArgumentException.class,
            () -> SocketCodec.encode(java.util.Collections.nCopies(5, new PaperItemService.SocketedGem(GemType.RUBY, 1))));
    }

    @Test void EncodingRejectsInvalidTiersAndUnknownTypes() {
        assertThrows(IllegalArgumentException.class,
            () -> SocketCodec.encode(List.of(new PaperItemService.SocketedGem(GemType.RUBY, 5))));
        assertThrows(IllegalArgumentException.class,
            () -> SocketCodec.encode(List.of(new PaperItemService.SocketedGem(null, 1))));
    }
}
