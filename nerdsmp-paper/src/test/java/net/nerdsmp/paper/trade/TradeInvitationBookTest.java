package net.nerdsmp.paper.trade;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class TradeInvitationBookTest {
    private final Instant now = Instant.parse("2026-09-13T12:00:00Z");

    @Test
    void joiningATradeInvalidatesIncomingAndOutgoingInvitations() {
        TradeInvitationBook book = new TradeInvitationBook();
        UUID player = UUID.randomUUID();
        UUID sender = UUID.randomUUID();
        UUID otherRecipient = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();
        book.send(sender, player, now.plusSeconds(60));
        book.send(player, otherRecipient, now.plusSeconds(60));
        book.send(sender, unrelated, now.plusSeconds(60));

        book.removeParticipant(player);

        assertNull(book.active(player, now));
        assertNull(book.active(otherRecipient, now));
        assertNotNull(book.active(unrelated, now));
    }

    @Test
    void anInvitationCannotBeAcceptedAtOrAfterItsExpiry() {
        TradeInvitationBook book = new TradeInvitationBook();
        UUID target = UUID.randomUUID();
        book.send(UUID.randomUUID(), target, now.plusSeconds(60));

        assertNotNull(book.active(target, now.plusSeconds(59)));
        assertNull(book.active(target, now.plusSeconds(60)));
        assertNull(book.active(target, now.plusSeconds(61)));
    }

    @Test
    void removingAnEarlierSenderDoesNotRemoveAReplacementRequest() {
        TradeInvitationBook book = new TradeInvitationBook();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        book.send(first, target, now.plusSeconds(60));
        book.send(second, target, now.plusSeconds(60));

        book.removeParticipant(first);

        assertEquals(second, book.active(target, now).source());
    }
}
