package net.nerdsmp.paper.anticheat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.UUID;

/** Data source behind the web console. Implementations must never hand HTTP threads live Bukkit objects. */
public interface PanelBackend {
    /** Default Gson output escapes <, >, & and quotes as unicode, keeping JSON inert if it is ever mis-sniffed. */
    Gson JSON = new Gson();

    static String json(JsonElement element) {
        return JSON.toJson(element);
    }

    /** Latest server snapshot as JSON. Called on HTTP threads, so it must return a prepared value. */
    String status();

    Reply player(UUID playerId);

    /** Applies a validated, CSRF-checked request from an authenticated panel login. */
    Reply act(String actor, JsonObject request);

    default void close() {
    }

    record Reply(int status, String json) {
        public static Reply ok(JsonObject body) {
            return new Reply(200, PanelBackend.json(body));
        }

        public static Reply error(int status, String message) {
            JsonObject body = new JsonObject();
            body.addProperty("error", message);
            return new Reply(status, PanelBackend.json(body));
        }
    }

    /** Used by the standalone test listener, which is not attached to a game server. */
    static PanelBackend offline() {
        return new PanelBackend() {
            @Override public String status() { return "{\"status\":\"starting\"}"; }
            @Override public Reply player(UUID playerId) { return Reply.error(503, "The server snapshot is not ready yet."); }
            @Override public Reply act(String actor, JsonObject request) { return Reply.error(503, "The server is not ready for actions yet."); }
        };
    }
}
