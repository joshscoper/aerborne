package net.nerdsmp.paper.presentation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nerdsmp.paper.market.PlayerStoreProfiles;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Player-chosen text must never turn into MiniMessage tags. The dangerous ones are click events: a planted
 * {@code <click:run_command:...>} makes whoever clicks run a command with their own permissions, staff included.
 */
final class MiniMessageInjectionTest {
    private static final String ATTACK = "<click:run_command:'/op Mallory'><hover:show_text:'free marks'>Click me</hover></click>";

    private static List<ClickEvent> clicks(Component component) {
        List<ClickEvent> found = new ArrayList<>();
        if (component.clickEvent() != null) found.add(component.clickEvent());
        if (component.hoverEvent() != null && component.hoverEvent().value() instanceof Component hover) found.addAll(clicks(hover));
        for (Component child : component.children()) found.addAll(clicks(child));
        return found;
    }

    private static String value(ClickEvent click) {
        return click.payload() instanceof ClickEvent.Payload.Text text ? text.value() : String.valueOf(click.payload());
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void escapedTextCarriesNoClickOrHoverAndReadsLiterally() {
        Component line = Chat.render("<gray>Store</gray> <white>" + Chat.escape(ATTACK) + "</white>");
        assertTrue(clicks(line).isEmpty(), "escaped text must not create a click event");
        assertTrue(plain(line).contains(ATTACK), "the player sees exactly what was typed");
    }

    @Test
    void unescapedTextWouldBeExploitable() {
        // Guards the test itself: without escape() the same input really does plant a command.
        Component line = Chat.render("<white>" + ATTACK + "</white>");
        assertEquals(List.of("/op Mallory"), clicks(line).stream().map(MiniMessageInjectionTest::value).toList());
    }

    @Test
    void quotesInAClickableCommandCannotCloseTheTagAndStartAnother() {
        String hostile = "pay Bob 1' ><click:run_command:'/op Mallory";
        Component line = Chat.render(Chat.command(hostile));
        List<String> commands = clicks(line).stream().map(MiniMessageInjectionTest::value).toList();
        assertEquals(List.of("/" + hostile), commands, "exactly one click, running the literal text");
    }

    @Test
    void usageLinesSuggestOnlyTheirOwnBaseCommand() {
        Component line = Chat.render(Chat.usage("/market search <" + ATTACK + ">"));
        List<String> commands = clicks(line).stream().map(MiniMessageInjectionTest::value).toList();
        assertEquals(List.of("/market search "), commands);
    }

    @Test
    void storeNamesRejectTagCharactersAndDescriptionsAreLeftForEscaping() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerStoreProfiles.Profile(ATTACK, ""));
        assertThrows(IllegalArgumentException.class, () -> new PlayerStoreProfiles.Profile("Shop<red>", ""));
        // Descriptions may contain '<' (it's plain text); every renderer escapes them.
        var profile = new PlayerStoreProfiles.Profile("Bob's Shop", "Best <prices> in town");
        Component lore = Chat.render("<gray>" + Chat.escape(profile.description()) + "</gray>");
        assertEquals("Best <prices> in town", plain(lore));
    }
}
