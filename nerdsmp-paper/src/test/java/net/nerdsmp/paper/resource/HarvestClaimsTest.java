package net.nerdsmp.paper.resource;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HarvestClaimsTest {
    @Test
    void oneEventSharesOneDecisionAndOperationAcrossRewardChannels() {
        HarvestClaims<Object> claims = new HarvestClaims<>();
        Object event = new Object();
        AtomicInteger eligibilityChecks = new AtomicInteger();
        HashSet<String> operations = new HashSet<>();

        for (HarvestClaims.Channel channel : HarvestClaims.Channel.values()) {
            var harvest = claims.claim(event, channel, () -> {
                eligibilityChecks.incrementAndGet();
                return true;
            }).orElseThrow();
            operations.add(harvest.operationId());
            assertTrue(harvest.operationId().startsWith("harvest:"));
            UUID.fromString(harvest.operationId().substring("harvest:".length()));
        }

        assertEquals(1, eligibilityChecks.get());
        assertEquals(1, operations.size());
    }

    @Test
    void eachRewardChannelCanConsumeAnEventOnlyOnce() {
        HarvestClaims<Object> claims = new HarvestClaims<>();
        Object event = new Object();

        for (HarvestClaims.Channel channel : HarvestClaims.Channel.values()) {
            assertTrue(claims.claim(event, channel, () -> true).isPresent(), channel.name());
            assertFalse(claims.claim(event, channel, () -> {
                throw new AssertionError("The shared decision must not be recalculated");
            }).isPresent(), channel.name());
        }
    }

    @Test
    void deniedEventRemainsDeniedEvenIfAnotherConsumerWouldAllowIt() {
        HarvestClaims<Object> claims = new HarvestClaims<>();
        Object event = new Object();
        AtomicInteger eligibilityChecks = new AtomicInteger();

        assertTrue(claims.claim(event, HarvestClaims.Channel.SKILLS, () -> {
            eligibilityChecks.incrementAndGet();
            return false;
        }).isEmpty());
        for (HarvestClaims.Channel channel : HarvestClaims.Channel.values()) {
            assertTrue(claims.claim(event, channel, () -> {
                eligibilityChecks.incrementAndGet();
                return true;
            }).isEmpty(), channel.name());
        }

        assertEquals(1, eligibilityChecks.get());
    }

    @Test
    void freshHarvestEventGetsFreshReceiptAndEligibilityDecision() {
        HarvestClaims<Object> claims = new HarvestClaims<>();
        AtomicInteger eligibilityChecks = new AtomicInteger();
        var first = claims.claim(new Object(), HarvestClaims.Channel.SKILLS, () -> {
            eligibilityChecks.incrementAndGet();
            return true;
        }).orElseThrow();
        var next = claims.claim(new Object(), HarvestClaims.Channel.SKILLS, () -> {
            eligibilityChecks.incrementAndGet();
            return true;
        }).orElseThrow();

        assertNotEquals(first.operationId(), next.operationId());
        assertEquals(2, eligibilityChecks.get());
    }

    @Test
    void denialDoesNotCarryOverToADifferentEvent() {
        HarvestClaims<Object> claims = new HarvestClaims<>();

        assertTrue(claims.claim(new Object(), HarvestClaims.Channel.EXCAVATION, () -> false).isEmpty());
        assertTrue(claims.claim(new Object(), HarvestClaims.Channel.EXCAVATION, () -> true).isPresent());
    }
}
