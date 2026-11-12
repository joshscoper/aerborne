package net.nerdsmp.paper.anticheat;
import net.nerdsmp.paper.anticheat.physics.MovementSimulator.Box;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CollisionWorkBudgetTest {
    @Test void ordinaryWalkingAndSmallKnockbackStayWithinBudget(){assertTrue(CollisionWorkBudget.allows(new Box(777,159,-52,782,164,-47),-64,320,0));assertTrue(CollisionWorkBudget.allows(new Box(0,60,0,12,70,12),-64,320,1000));}
    @Test void staleTeleportVelocityCannotEnumerateMillionsOfBlocks(){assertFalse(CollisionWorkBudget.allows(new Box(-800,-80,-800,800,300,800),-64,320,0));assertFalse(CollisionWorkBudget.allows(new Box(-30_000_000,-64,-30_000_000,30_000_000,320,30_000_000),-64,320,0));}
    @Test void nonFiniteAndReversedBoundsFailClosed(){assertFalse(CollisionWorkBudget.allows(new Box(Double.NaN,0,0,1,1,1),-64,320,0));assertFalse(CollisionWorkBudget.allows(new Box(0,0,0,Double.POSITIVE_INFINITY,1,1),-64,320,0));assertFalse(CollisionWorkBudget.allows(new Box(2,0,0,1,1,1),-64,320,0));}
    @Test void cacheBudgetIsIndependentOfOneQueriesSize(){assertFalse(CollisionWorkBudget.allows(new Box(0,64,0,1,65,1),-64,320,8192));assertTrue(CollisionWorkBudget.allows(new Box(0,64,0,1,65,1),-64,320,8191));}
    @Test void outOfHeightQueriesScanNoBlocks(){assertTrue(CollisionWorkBudget.allows(new Box(0,400,0,2,410,2),-64,320,0));}
    @Test void FiniteCoordinatesCannotOverflowIntegerEnumeration(){assertFalse(CollisionWorkBudget.allows(new Box(1e100,64,0,1e100,65,1),-64,320,0));assertFalse(CollisionWorkBudget.allows(new Box(0,-1e100,0,1,65,1),-64,320,0));}
}
