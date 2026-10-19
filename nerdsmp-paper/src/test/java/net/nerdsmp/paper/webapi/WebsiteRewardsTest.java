package net.nerdsmp.paper.webapi;

import com.sun.net.httpserver.HttpServer;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** Arcade payouts pulled from the website and credited exactly once. */
class WebsiteRewardsTest {
    private static final String TOKEN = "e".repeat(64);
    private static final String ACCOUNT = "11111111-2222-3333-4444-555555555555";

    /** Remembers idempotency keys like the real economy does. */
    static final class FakeEconomy implements EconomyService {
        final List<String> applied = new ArrayList<>();
        final Set<String> keys = new HashSet<>();
        long total;

        @Override public CompletionStage<Long> balance(AccountId accountId) { return CompletableFuture.completedFuture(total); }
        @Override public CompletionStage<TransactionResult> credit(AccountId accountId, long marks, TransactionReason reason, String key) {
            if (!keys.add(key)) return CompletableFuture.completedFuture(new TransactionResult(TransactionResult.Status.DUPLICATE, total, OptionalLong.empty()));
            applied.add(key + ":" + marks + ":" + reason.code());
            total += marks;
            return CompletableFuture.completedFuture(TransactionResult.applied(total));
        }
        @Override public CompletionStage<TransactionResult> debit(AccountId a, long m, TransactionReason r, String k) { throw new UnsupportedOperationException(); }
        @Override public CompletionStage<TransactionResult> transfer(AccountId a, AccountId b, long m, TransactionReason r, String k) { throw new UnsupportedOperationException(); }
    }

    private static String payouts(long... idAndMarks) {
        StringBuilder json = new StringBuilder("{\"apiVersion\":1,\"payouts\":[");
        for (int i = 0; i < idAndMarks.length; i += 2) {
            if (i > 0) json.append(',');
            json.append("{\"id\":").append(idAndMarks[i]).append(",\"accountId\":\"").append(ACCOUNT)
                .append("\",\"marks\":").append(idAndMarks[i + 1]).append(",\"game\":\"sky-hop\"}");
        }
        return json.append("]}").toString();
    }

    @Test void payoutsAreCreditedOnceAndAcknowledged() throws Exception {
        var answer = new AtomicReference<>(payouts(1, 10, 2, 5));
        var queries = new ArrayList<String>();
        var auth = new AtomicReference<String>();
        HttpServer website = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
        website.createContext("/admin/api/arcade-payouts", exchange -> {
            queries.add(exchange.getRequestURI().getQuery());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = answer.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        website.start();
        try {
            var economy = new FakeEconomy();
            var settings = new WebsiteRewards.Settings(URI.create("http://127.0.0.1:" + website.getAddress().getPort() + "/admin/api/arcade-payouts"), 60, 50, 5000);
            try (var rewards = new WebsiteRewards(settings, TOKEN, economy, Runnable::run, Logger.getAnonymousLogger(), System::currentTimeMillis)) {
                assertEquals(2, rewards.pollOnce());
                assertEquals(List.of("website-arcade:1:10:website.arcade", "website-arcade:2:5:website.arcade"), economy.applied);
                assertEquals("Bearer " + TOKEN, auth.get());
                assertEquals(2, rewards.acknowledged());
                // The website hasn't marked them delivered yet and sends them again, plus a new one.
                answer.set(payouts(1, 10, 2, 5, 3, 7));
                assertEquals(1, rewards.pollOnce());
                assertEquals(22, economy.total, "Old payouts are never paid twice");
                assertEquals(List.of("ack=0", "ack=2"), queries);
                // Too large for one payout: skipped, acknowledged, not paid.
                answer.set(payouts(4, 500));
                assertEquals(0, rewards.pollOnce());
                assertEquals(4, rewards.acknowledged());
                assertEquals(22, economy.total);
            }
        } finally {
            website.stop(0);
        }
    }

    @Test void anHourlyCeilingStopsPayoutsUntilRestart() throws Exception {
        HttpServer website = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
        website.createContext("/p", exchange -> {
            byte[] bytes = payouts(1, 40, 2, 40, 3, 40).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        website.start();
        try {
            var economy = new FakeEconomy();
            var settings = new WebsiteRewards.Settings(URI.create("http://127.0.0.1:" + website.getAddress().getPort() + "/p"), 60, 50, 100);
            try (var rewards = new WebsiteRewards(settings, TOKEN, economy, Runnable::run, Logger.getAnonymousLogger(), System::currentTimeMillis)) {
                assertEquals(2, rewards.pollOnce());
                assertEquals(80, economy.total);
                assertEquals(0, rewards.pollOnce(), "Paused after the ceiling");
            }
        } finally {
            website.stop(0);
        }
    }

    @Test void malformedOrUnsafeResponsesAreRejected() {
        assertThrows(java.io.IOException.class, () -> WebsiteRewards.parse("not json"));
        assertThrows(java.io.IOException.class, () -> WebsiteRewards.parse("{\"apiVersion\":2,\"payouts\":[]}"));
        assertThrows(java.io.IOException.class, () -> WebsiteRewards.parse(payouts(2, 5, 1, 5)), "ids must increase");
        assertThrows(java.io.IOException.class, () -> WebsiteRewards.parse(payouts(1, 0)), "no zero payouts");
        assertThrows(IllegalArgumentException.class, () -> new WebsiteRewards.Settings(URI.create("http://aerborne.net/x"), 60, 50, 5000));
        assertThrows(IllegalArgumentException.class, () -> new WebsiteRewards.Settings(URI.create("https://aerborne.net/x"), 60, 50, 10));
    }
}
