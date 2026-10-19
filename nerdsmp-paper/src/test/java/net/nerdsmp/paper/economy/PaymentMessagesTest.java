package net.nerdsmp.paper.economy;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nerdsmp.paper.presentation.Chat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class PaymentMessagesTest {
    @Test
    void everyReceiptPhaseReadsAsAWord() {
        for (PlayerPayments.Phase phase : PlayerPayments.Phase.values()) {
            String text = PlainTextComponentSerializer.plainText().serialize(Chat.render(PaymentController.phase(phase)));
            assertFalse(text.isBlank() || text.contains("_") || text.equals(text.toUpperCase()), phase.name());
        }
    }

    @Test
    void marksPayIsASilentAliasOfPay() throws IOException {
        // /marks pay reaches this handler too; players are only ever told about /pay.
        String code = Files.readString(Path.of("src", "main", "java", "net", "nerdsmp", "paper", "economy", "PaymentController.java"));
        assertFalse(code.contains("marks pay\""), "PaymentController should not advertise /marks pay");
        assertEquals("/pay <player> <amount>", PaymentController.USAGE);
    }
}
