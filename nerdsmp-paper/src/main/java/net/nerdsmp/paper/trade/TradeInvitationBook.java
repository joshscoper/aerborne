package net.nerdsmp.paper.trade;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pending invitations; a participant entering a trade invalidates all their other requests. */
final class TradeInvitationBook {
    private final Map<UUID, Request> requests = new HashMap<>();

    record Request(UUID source, Instant expiresAt) {
    }

    void send(UUID source, UUID target, Instant expiresAt) {
        requests.put(target, new Request(source, expiresAt));
    }

    Request active(UUID target, Instant now) {
        Request request = requests.get(target);
        if (request != null && request.expiresAt().isAfter(now)) {
            return request;
        }
        requests.remove(target);
        return null;
    }

    void removeTarget(UUID target) {
        requests.remove(target);
    }

    void removeParticipant(UUID participant) {
        requests.remove(participant);
        requests.values().removeIf(request -> request.source().equals(participant));
    }
}
