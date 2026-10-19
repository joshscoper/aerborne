package net.nerdsmp.paper.content;

import net.nerdsmp.paper.engagement.QuestAction;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Wiring checks supplement the behavior tests; they are not multiplayer acceptance tests. */
class SkyblockSocialReleaseContractTest {
    private String source(String path) throws Exception { return Files.readString(Path.of("src/main/java/net/nerdsmp/paper/" + path)); }
    private YamlConfiguration catalog(String file) throws Exception {
        try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/content/" + file)), StandardCharsets.UTF_8)) {
            var yaml = new YamlConfiguration(); yaml.load(reader); return yaml;
        }
    }
    @Test void skyblockContractsPreserveRewardIdsAndThresholds() throws Exception {
        var original = catalog("daily-quests.yml"); var sky = catalog("skyblock-daily-quests.yml");
        assertEquals(original.getConfigurationSection("quests").getKeys(false), sky.getConfigurationSection("quests").getKeys(false));
        for (String id : original.getConfigurationSection("quests").getKeys(false)) {
            String path = "quests." + id;
            assertEquals(original.getLong(path + ".marks"), sky.getLong(path + ".marks"));
            assertEquals(original.getInt(path + ".target"), sky.getInt(path + ".target"));
            assertDoesNotThrow(() -> QuestAction.valueOf(sky.getString(path + ".action").toUpperCase(Locale.ROOT)));
            assertFalse(sky.getString(path + ".description").isBlank());
        }
        assertTrue(sky.getString("quests.seam_work.description").contains("generator"));
        assertTrue(source("engagement/EngagementService.java").contains("? \"content/skyblock-daily-quests.yml\""));
    }
    @Test void skyblockCannotUseLegacyRemoteOrderSales() throws Exception {
        String command = source("command/CommandRouter.java");
        int fulfill = command.indexOf("if (args.length == 3 && args[0].equalsIgnoreCase(\"fulfill\"))");
        int remove = command.indexOf("held.setAmount(held.getAmount() - amount)", fulfill);
        assertTrue(command.substring(fulfill, remove).contains("if (plugin.skyblockEnabled())"));
        assertTrue(source("job/ServerOrderService.java").contains("definitions = enabled ? load(plugin) : Map.of()"));
        assertTrue(source("gui/MenuManager.java").contains("if (plugin.skyblockEnabled()) { openCityMerchants(player); return; }"));
    }
    @Test void communityMenuNoLongerReopensItselfAndCoopChatHasAButton() throws Exception {
        String menu = source("gui/MenuManager.java");
        String community = menu.substring(menu.indexOf("private void openCommunity"), menu.indexOf("public void open(Player player, View view)"));
        assertFalse(community.contains("\"social\"")); assertTrue(community.contains("\"channel\""));
        assertTrue(source("skyblock/SkyblockController.java").contains("\"Co-op chat\""));
        assertTrue(source("player/PlayerSettingsService.java").contains("COOP_INVITES(\"coop-invites\""));
        assertTrue(source("player/PlayerSettingsService.java").contains("PARTY_REQUESTS(\"party-requests\""));
    }
    @Test void loginRecoversTradeBeforeAnyStarterInventoryAndRejectsParallelLinkedSessions() throws Exception {
        String login = source("listener/SessionListener.java");
        assertTrue(login.indexOf("nerdSmp.trade().recover(player)") < login.indexOf("starterKit.grantIfNeeded"));
        assertTrue(login.contains("Bukkit.getPlayer(player.getUniqueId()) == player"));
        assertTrue(login.contains("loginTokens.get(player.getUniqueId()) == token"));
        assertTrue(login.contains("already playing on another linked client"));
    }
    @Test void finalTradeConfirmationRequiresBothReviewWindowsAndUsesDurableSaveBeforeDecision() throws Exception {
        String trade = source("trade/TradeManager.java");
        assertTrue(trade.contains("s.left.getOpenInventory().getTopInventory() == s.inventory"));
        assertTrue(trade.contains("s.right.getOpenInventory().getTopInventory() == s.inventory"));
        assertTrue(trade.indexOf("saves.saveAndVerify(session.left)") < trade.indexOf("journal.begin("));
        assertTrue(trade.indexOf("journal.begin(") < trade.indexOf("boolean first = recover(session.left)"));
    }
    @Test void diagnosticsDoNotCallAnUnfinishedBuildReady() throws Exception {
        String release = source("release/ReleaseReadiness.java");
        assertTrue(release.contains("not a launch approval")); assertTrue(release.contains("Saved warps"));
        assertTrue(release.contains("crash boundaries")); assertTrue(release.contains("Java multiplayer"));
    }
}
