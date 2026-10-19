package net.nerdsmp.paper.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ContentRosterTest {
    private static final Pattern DEFINITION = Pattern.compile("(?m)^  [a-z0-9_]+:$");

    @Test
    void betaRosterShipsTwelveVariantsAndThreeBosses() throws IOException {
        String mobs = resource("/content/mobs.yml");
        assertEquals(15L, DEFINITION.matcher(mobs).results().count());
        assertEquals(3L, Pattern.compile("(?m)^    boss: true$").matcher(mobs).results().count());
    }

    @Test
    void economyShipsThreeCappedServerOrders() throws IOException {
        String orders = resource("/content/orders.yml");
        assertEquals(3L, DEFINITION.matcher(orders).results().count());
        assertEquals(3L, Pattern.compile("(?m)^    item-cap: [1-9][0-9]*$").matcher(orders).results().count());
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = Objects.requireNonNull(getClass().getResourceAsStream(path), path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
