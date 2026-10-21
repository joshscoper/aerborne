package net.nerdsmp.paper.chat;

import com.google.gson.JsonParser;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatConsoleTest {
    private final AtomicLong clock = new AtomicLong(1_700_000_000_000L);
    private final ChatConsole chat = new ChatConsole(clock::get);
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();

    @Test void pauseCoversEveryPublicEntryPointButNotPrivateChannelsOrStaff() {
        chat.configure(new ChatConsole.Settings(true, 30));
        for (String channel : new String[]{"global", "local", "trade"}) {
            assertNotNull(chat.refuse(alice, channel, false));
            assertNull(chat.refuse(alice, channel, true));
        }
        for (String channel : new String[]{"staff", "party", "island", "homestead"})
            assertNull(chat.refuse(alice, channel, false));
    }

    @Test void slowModeIsSharedAcrossPublicChannelsPerPlayerAndExpiresExactly() {
        chat.configure(new ChatConsole.Settings(false, 10));
        assertNull(chat.refuse(alice, "global", false));
        assertNotNull(chat.refuse(alice, "trade", false));
        assertNull(chat.refuse(bob, "local", false));
        clock.addAndGet(9_999);
        assertTrue(chat.refuse(alice, "local", false).contains("1 seconds"));
        clock.incrementAndGet();
        assertNull(chat.refuse(alice, "local", false));
    }

    @Test void privateAndStaffMessagesDoNotConsumePublicCooldowns() {
        chat.configure(new ChatConsole.Settings(false, 20));
        assertNull(chat.refuse(alice, "party", false));
        assertNull(chat.refuse(alice, "staff", false));
        assertNull(chat.refuse(alice, "trade", true));
        assertNull(chat.refuse(alice, "global", false));
        assertNotNull(chat.refuse(alice, "global", false));
    }

    @Test void changingControlsAndLeavingClearOldCooldowns() {
        chat.configure(new ChatConsole.Settings(false, 10));
        assertNull(chat.refuse(alice, "global", false));
        chat.configure(new ChatConsole.Settings(false, 0));
        assertNull(chat.refuse(alice, "global", false));
        assertNull(chat.refuse(alice, "global", false));
        chat.configure(new ChatConsole.Settings(false, 10));
        assertNull(chat.refuse(alice, "global", false));
        chat.forget(alice);
        assertNull(chat.refuse(alice, "global", false));
    }

    @Test void recordIsBoundedChronologicalAndDetached() {
        for (int i = 0; i < 400; i++) {
            chat.record("global", alice, "Alice", "Message " + i, "Skyhold", null, 2, false);
            clock.incrementAndGet();
        }
        var snapshot = chat.snapshot();
        var messages = snapshot.getAsJsonArray("messages");
        assertEquals(ChatConsole.CAPACITY, messages.size());
        assertEquals("Message 100", messages.get(0).getAsJsonObject().get("message").getAsString());
        assertEquals("Message 399", messages.get(299).getAsJsonObject().get("message").getAsString());
        messages.remove(0);
        assertEquals(300, chat.snapshot().getAsJsonArray("messages").size());
        assertNotEquals(chat.snapshot().getAsJsonArray("messages").get(0).getAsJsonObject().get("id"),
            chat.snapshot().getAsJsonArray("messages").get(1).getAsJsonObject().get("id"));
    }

    @Test void privatePlayerChannelsAreNeverRetainedAndInvalidMessagesAreIgnored() {
        for (String channel : new String[]{"party", "island", "homestead", "dm", "direct", "unknown"})
            chat.record(channel, alice, "Alice", "Private", null, null, 2, false);
        chat.record("global", alice, "Alice", " ", null, null, 1, false);
        chat.record("global", alice, "Alice", "a".repeat(513), null, null, 1, false);
        assertEquals(0, chat.snapshot().getAsJsonArray("messages").size());
        chat.record("direct", null, "Panel (admin)", "Support reply", null, "Alice", 1, true);
        assertTrue(chat.snapshot().getAsJsonArray("messages").get(0).getAsJsonObject().get("panel").getAsBoolean());
    }

    @Test void strictSettingsRejectCoercionFractionsAndOutOfRangeValues() {
        for (String json : new String[]{"{}", "{\"paused\":\"false\",\"slowSeconds\":5}",
            "{\"paused\":false,\"slowSeconds\":\"5\"}", "{\"paused\":false,\"slowSeconds\":1.5}",
            "{\"paused\":false,\"slowSeconds\":-1}", "{\"paused\":false,\"slowSeconds\":121}",
            "{\"paused\":false,\"slowSeconds\":1e30}"})
            assertThrows(IllegalArgumentException.class, () -> ChatConsole.parseSettings(JsonParser.parseString(json).getAsJsonObject()), json);
        assertEquals(new ChatConsole.Settings(true, 120), ChatConsole.parseSettings(JsonParser.parseString("{\"paused\":true,\"slowSeconds\":120}").getAsJsonObject()));
    }

    @Test void restartDefaultsToOpenWithoutHistoryAndJsonRemainsInert() {
        chat.configure(new ChatConsole.Settings(true, 5));
        chat.record("global", alice, "Alice", "<script>alert('x')</script>", "Skyhold", null, 2, false);
        assertFalse(net.nerdsmp.paper.anticheat.PanelBackend.json(chat.snapshot()).contains("<script>"));
        var restarted = new ChatConsole(clock::get).snapshot();
        assertFalse(restarted.get("paused").getAsBoolean());
        assertEquals(0, restarted.get("slowSeconds").getAsInt());
        assertEquals(0, restarted.getAsJsonArray("messages").size());
    }
}
