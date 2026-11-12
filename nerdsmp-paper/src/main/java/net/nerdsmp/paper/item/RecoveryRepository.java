package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.PlayerDataStore;

/** Absolute expiry survives logout and restart; offline time never heals the player. */
public final class RecoveryRepository {
    private final PlayerDataStore data;
    public RecoveryRepository(PlayerDataStore data) { this.data = data; }
    public RecoveryRules.WellFed load(AccountId account) {
        return data.read(account, yaml -> {
            if (!yaml.contains("food.v1")) return RecoveryRules.WellFed.NONE;
            if (!yaml.isLong("food.v1.until") && !yaml.isInt("food.v1.until")
                || !yaml.isDouble("food.v1.recovery-percent") && !yaml.isInt("food.v1.recovery-percent"))
                throw new IllegalStateException("Malformed food recovery data for " + account);
            return new RecoveryRules.WellFed(yaml.getLong("food.v1.until"), yaml.getDouble("food.v1.recovery-percent"));
        });
    }
    public void save(AccountId account, RecoveryRules.WellFed food) {
        data.update(account, yaml -> {
            yaml.set("food.v1.until", food.until());
            yaml.set("food.v1.recovery-percent", food.recoveryPercent());
        });
    }
}
