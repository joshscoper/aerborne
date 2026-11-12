package net.nerdsmp.paper.mob;

import org.bukkit.event.entity.EntityTransformEvent.TransformReason;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class EncounterBehaviorTest {
    @Test void accidentalConversionsCannotReplaceEncounterIdentity() {
        for (var reason : new TransformReason[]{TransformReason.DROWNED, TransformReason.FROZEN,
            TransformReason.PIGLIN_ZOMBIFIED, TransformReason.INFECTION, TransformReason.CURED, TransformReason.LIGHTNING})
            assertTrue(EncounterBehavior.changesIdentity(reason), reason.name());
    }
    @Test void ordinaryLifecycleAndScriptedTransformationsAreNotBlanketBlocked() {
        for (var reason : new TransformReason[]{TransformReason.SPLIT, TransformReason.METAMORPHOSIS,
            TransformReason.SHEARED, TransformReason.UNKNOWN}) assertFalse(EncounterBehavior.changesIdentity(reason),reason.name());
    }
}
