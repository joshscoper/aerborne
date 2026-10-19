package net.nerdsmp.smoke;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;

/** Actual Paper wiring checks. The HTTP side reads cached snapshots, never calls this server thread. */
final class WebApiSmoke {
    private static final String FIXTURE_TOKEN = "a".repeat(64);

    static void run(NerdSmpPlugin core, BiConsumer<Boolean, String> check) throws Exception {
        var api = core.webApi();
        check.accept(api != null && api.bindAddress().isLoopbackAddress() && api.port() > 0,
            "web API starts with isolated loopback and an ephemeral fixture port");
        api.refreshNow();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .version(HttpClient.Version.HTTP_1_1).build()) {
            var status = request(client, core, "/api/v1/status", false);
            JsonObject json = JsonParser.parseString(status.body()).getAsJsonObject();
            check.accept(status.statusCode() == 200 && json.get("ready").getAsBoolean()
                && json.get("name").getAsString().equals(core.displayName()),
                "public API returns the live brand and readiness without authentication");
            check.accept(json.get("onlinePlayers").getAsInt() == Bukkit.getOnlinePlayers().size()
                && json.keySet().stream().noneMatch(key -> List.of("players", "uuid", "ip", "telemetry", "heapUsedBytes").contains(key)),
                "public API publishes only aggregate player counts and presentation metadata");
            check.accept(status.headers().firstValue("connection").orElse("").equalsIgnoreCase("close")
                && status.headers().firstValue("cache-control").orElse("").equals("no-store"),
                "API closes connections and prevents snapshot caching");
            check.accept(request(client, core, "/api/v1/telemetry", false).statusCode() == 401,
                "private telemetry refuses anonymous access");
            var telemetry = request(client, core, "/api/v1/telemetry", true);
            var values = JsonParser.parseString(telemetry.body()).getAsJsonObject();
            check.accept(telemetry.statusCode() == 200 && values.has("averageTickMs")
                && values.has("arcaneOperations") && values.has("islandStacks"),
                "authenticated API serves main-thread performance, enchanting and stack snapshots");
            var catalog = request(client, core, "/api/v1/catalog", true);
            var content = JsonParser.parseString(catalog.body()).getAsJsonObject();
            check.accept(catalog.statusCode() == 200 && content.get("items").getAsInt() == core.items().itemIds().size()
                && content.get("readOnly").getAsBoolean(),
                "private catalog reflects the loaded item registry without exposing inventories");
            check.accept(request(client, core, "/api/v1/status?token=ignored", false).statusCode() == 400,
                "API refuses query-string credential or parameter ambiguity");
            var post = HttpRequest.newBuilder(uri(core, "/api/v1/status")).timeout(Duration.ofSeconds(2))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
            check.accept(client.send(post, HttpResponse.BodyHandlers.ofString()).statusCode() == 405,
                "API has no command or mutation method");
        }
        check.accept(!api.status().contains(FIXTURE_TOKEN), "operator API diagnostics never print a token");
        check.accept(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "aerborneadmin api")
            && Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "aerborneadmin stacks"),
            "operator API and stack diagnostics are wired through the Aerborne admin alias");
    }

    private static URI uri(NerdSmpPlugin core, String path) {
        return URI.create("http://127.0.0.1:" + core.webApi().port() + path);
    }

    private static HttpResponse<String> request(HttpClient client, NerdSmpPlugin core, String path, boolean authenticated)
        throws Exception {
        var builder = HttpRequest.newBuilder(uri(core, path)).timeout(Duration.ofSeconds(2)).GET();
        if (authenticated) builder.header("Authorization", "Bearer " + FIXTURE_TOKEN);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
