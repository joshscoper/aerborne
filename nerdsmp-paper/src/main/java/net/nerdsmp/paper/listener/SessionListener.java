package net.nerdsmp.paper.listener;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.api.service.AccountService;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.identity.IdentityProvider;
import net.nerdsmp.paper.achievement.AchievementService;
import net.nerdsmp.paper.item.StarterKitService;
import net.nerdsmp.paper.session.SessionRegistry;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class SessionListener implements Listener {
    private final JavaPlugin plugin;
    private final IdentityProvider identities;
    private final AccountService accounts;
    private final EconomyService economy;
    private final SessionRegistry sessions;
    private final StarterKitService starterKit;
    private final PlayerDataStore playerData;
    private final AchievementService achievements;
    private final long starterMarks;
    private final java.util.Map<java.util.UUID, Object> loginTokens = new java.util.HashMap<>();

    public SessionListener(
        JavaPlugin plugin,
        IdentityProvider identities,
        AccountService accounts,
        EconomyService economy,
        SessionRegistry sessions,
        StarterKitService starterKit,
        PlayerDataStore playerData,
        AchievementService achievements,
        long starterMarks
    ) {
        this.plugin = plugin;
        this.identities = identities;
        this.accounts = accounts;
        this.economy = economy;
        this.sessions = sessions;
        this.starterKit = starterKit;
        this.playerData = playerData;
        this.achievements = achievements;
        this.starterMarks = starterMarks;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Object token = new Object(); loginTokens.put(player.getUniqueId(), token);
        if (plugin instanceof net.nerdsmp.paper.NerdSmpPlugin nerdSmp && !nerdSmp.trade().recover(player)) return;
        PlatformIdentity identity = identities.identity(player);
        accounts.resolveOrCreate(identity).whenComplete((accountId, lookupError) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!current(player, token)) return;
            if (lookupError != null) { finish(player, null, token, lookupError); return; }
            try {
                playerData.bind(player.getUniqueId(), accountId, identity, player.getName());
                economy.credit(accountId, starterMarks, new EconomyService.TransactionReason("onboarding.starter", "Starting balance"), "starter:" + accountId)
                    .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> finish(player, accountId, token, error)));
            } catch (RuntimeException error) { finish(player, accountId, token, error); }
        }));
    }

    private boolean current(Player player, Object token) {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player && loginTokens.get(player.getUniqueId()) == token;
    }
    private void finish(Player player, AccountId accountId, Object token, Throwable error) {
            if (!current(player, token)) {
                return;
            }
            if (error != null) {
                plugin.getLogger().severe("Could not initialize player " + player.getName() + ": " + error.getMessage());
                player.kick(net.kyori.adventure.text.Component.text("Your account could not be loaded. Please try again."));
                return;
            }
            var otherSession = sessions.player(accountId).filter(id -> !id.equals(player.getUniqueId()));
            if (otherSession.isPresent() && Bukkit.getPlayer(otherSession.get()) != null) {
                player.kick(net.kyori.adventure.text.Component.text("This account is already playing on another linked client. Disconnect it before joining here."));
                return;
            }
            sessions.join(player.getUniqueId(), accountId);
            if (plugin instanceof net.nerdsmp.paper.NerdSmpPlugin nerdSmp) {
                if (!nerdSmp.trade().recover(player)) return;
                nerdSmp.settings().invalidate(player.getUniqueId());
                try { nerdSmp.social().ready(player); }
                catch (RuntimeException failure) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Social profile failed to load", failure); player.kick(net.kyori.adventure.text.Component.text("Your social profile could not be loaded. Please contact staff.")); return; }
                player.sendMessage(nerdSmp.branding().welcome());
            }
            net.nerdsmp.paper.presentation.Chat.info(player,
                "Start with " + net.nerdsmp.paper.presentation.Chat.command("quickstart")
                    + ". Your shortcuts are in " + net.nerdsmp.paper.presentation.Chat.command("menu") + ".");
            try {
                if (starterKit.grantIfNeeded(accountId, player) == StarterKitService.GrantResult.GRANTED) {
                    if (plugin instanceof net.nerdsmp.paper.NerdSmpPlugin nerdSmp) player.sendMessage(nerdSmp.branding().firstSteps());
                    player.sendRichMessage("<green>Your starter gear is ready.</green>");
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("Could not grant starter kit to " + player.getName() + ": " + exception.getMessage());
                player.sendRichMessage("<red>Your starter kit could not be delivered. Please contact staff.</red>");
            }
            achievements.onSessionReady(player);
            if (plugin instanceof net.nerdsmp.paper.NerdSmpPlugin nerdSmp) {
                nerdSmp.collections().onSessionReady(player);
                nerdSmp.jobs().recover(accountId).exceptionally(recoveryError -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Job payment recovery held for " + accountId, recoveryError);
                    return null;
                });
                nerdSmp.marketController().onSessionReady(player);
                if (nerdSmp.skyblock() != null) nerdSmp.skyblock().ready(player);
            }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        loginTokens.remove(event.getPlayer().getUniqueId());
        sessions.quit(event.getPlayer().getUniqueId());
    }
}
