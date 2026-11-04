package net.nerdsmp.paper.vote;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Listens for votes from server list sites. Each connection gets a few seconds to send one vote; a handful of
 * worker threads read them, so a slow or hostile client never touches the main thread. Votes that check out go
 * to {@code votes}, on a worker thread.
 */
final class VotifierListener implements AutoCloseable {
    private static final int TIMEOUT_MILLIS = 5000;
    private static final int PER_ADDRESS_PER_MINUTE = 30;
    private static final long WARN_EVERY_MILLIS = 600_000L;

    private final ServerSocket server;
    private final ThreadPoolExecutor workers;
    private final Map<String, String> tokens;
    private final PrivateKey key;
    private final Consumer<VotifierProtocol.Vote> votes;
    private final Logger logger;
    private final SecureRandom random = new SecureRandom();
    private final Map<InetAddress, Deque<Long>> recent = new HashMap<>(); // Only the accept thread uses this.
    private final Map<InetAddress, Long> warned = new ConcurrentHashMap<>();
    private volatile boolean closed;

    VotifierListener(InetSocketAddress bind, Map<String, String> tokens, PrivateKey key, Consumer<VotifierProtocol.Vote> votes, Logger logger) throws IOException {
        this.tokens = Map.copyOf(tokens);
        this.key = key;
        this.votes = votes;
        this.logger = logger;
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(bind, 16);
        workers = new ThreadPoolExecutor(1, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), runnable -> {
            Thread thread = new Thread(runnable, "NerdSMP-Votifier");
            thread.setDaemon(true);
            return thread;
        });
        Thread acceptor = new Thread(this::accept, "NerdSMP-Votifier-Accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    int port() { return server.getLocalPort(); }

    private void accept() {
        while (!closed && !server.isClosed()) {
            Socket socket;
            try { socket = server.accept(); }
            catch (IOException failure) { continue; }
            if (!allowed(socket.getInetAddress())) { closeQuietly(socket); continue; }
            try { workers.execute(() -> handle(socket)); }
            catch (RejectedExecutionException busy) { closeQuietly(socket); }
        }
    }

    private boolean allowed(InetAddress address) {
        long now = System.currentTimeMillis();
        if (recent.size() > 4096) recent.clear();
        Deque<Long> times = recent.computeIfAbsent(address, ignored -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() > 60_000L) times.removeFirst();
        if (times.size() >= PER_ADDRESS_PER_MINUTE) return false;
        times.addLast(now);
        return true;
    }

    private void handle(Socket socket) {
        OutputStream out = null;
        try {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            String challenge = VotifierProtocol.challenge(random);
            out = socket.getOutputStream();
            out.write(VotifierProtocol.greeting(challenge));
            out.flush();
            VotifierProtocol.Vote vote = VotifierProtocol.read(new DataInputStream(new BufferedInputStream(socket.getInputStream())), challenge, tokens, key);
            votes.accept(vote);
            if (vote.v2()) {
                out.write(VotifierProtocol.ok());
                out.flush();
            }
        } catch (VotifierProtocol.Refused refused) {
            warn(socket.getInetAddress(), refused.getMessage());
            if (refused.v2 && out != null) {
                try { out.write(VotifierProtocol.error(refused.getMessage())); out.flush(); }
                catch (IOException ignored) { }
            }
        } catch (IOException | RuntimeException ignored) {
            // Timeouts, port scanners and dropped connections: nothing to tell anyone.
        } finally {
            closeQuietly(socket);
        }
    }

    private void warn(InetAddress address, String why) {
        long now = System.currentTimeMillis();
        Long last = warned.get(address);
        if (last != null && now - last < WARN_EVERY_MILLIS) return;
        if (warned.size() > 1024) warned.clear();
        warned.put(address, now);
        logger.warning("Refused a vote from " + address.getHostAddress() + ": " + why);
    }

    private static void closeQuietly(Socket socket) {
        try { socket.close(); } catch (IOException ignored) { }
    }

    @Override
    public void close() {
        closed = true;
        try { server.close(); } catch (IOException ignored) { }
        workers.shutdownNow();
    }
}
