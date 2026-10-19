package net.nerdsmp.paper.pvp;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.PartyService;
import net.nerdsmp.paper.session.SessionRegistry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PvpManager implements Listener {
    private final SessionRegistry sessions;
    private final PartyService parties;
    private final Set<AccountId> enabled = ConcurrentHashMap.newKeySet();

    public PvpManager(SessionRegistry sessions, PartyService parties) {
        this.sessions = sessions;
        this.parties = parties;
    }

    public void setEnabled(AccountId accountId, boolean value) {
        if (value) {
            enabled.add(accountId);
        } else {
            enabled.remove(accountId);
        }
    }

    public boolean enabled(AccountId accountId) {
        return enabled.contains(accountId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        AccountId attackerAccount = sessions.account(attacker.getUniqueId()).orElse(null);
        AccountId victimAccount = sessions.account(victim.getUniqueId()).orElse(null);
        if (attackerAccount != null && victimAccount != null
            && parties.sameParty(attackerAccount, victimAccount).toCompletableFuture().getNow(false)) {
            event.setCancelled(true);
            attacker.sendRichMessage("<gold>Party members cannot damage each other.</gold>");
            return;
        }
        if (attackerAccount == null || victimAccount == null
            || !enabled(attackerAccount) || !enabled(victimAccount)) {
            event.setCancelled(true);
            attacker.sendRichMessage("<red>PvP requires both players to opt in with <white>/duel on</white>.</red>");
        }
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
