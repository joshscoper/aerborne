package net.nerdsmp.paper.gathering;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

/** Protection/reward bridge; an accepted event is not a completed harvest until replant commits. */
public final class HoeHarvestBreakEvent extends BlockBreakEvent {
    private boolean committed;
    private boolean rewardEligible;
    public HoeHarvestBreakEvent(Block block, Player player) { super(block, player); }
    public boolean committed() { return committed; }
    public boolean rewardEligible() { return rewardEligible; }
    public void markRewardEligible() { rewardEligible = true; }
    void commit() {
        if (committed || isCancelled() || !isDropItems()) throw new IllegalStateException("Harvest cannot be committed");
        committed = true;
    }
}
