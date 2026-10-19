package net.nerdsmp.paper.webapi;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpDecoderConfig;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.concurrent.DefaultEventExecutorChooserFactory;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.ThreadPerTaskExecutor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Small, body-free HTTP/1.1 boundary. Two owned event-loop threads, bounded task queues and 64 live
 * connections; every socket has an absolute five-second lifetime and handles only one request.
 * The game thread is never called, scheduled or waited on by this listener.
 */
final class ApiHttpListener implements AutoCloseable {
    static final int MAX_CONNECTIONS = 64;
    private static final int SOCKET_SECONDS = 5;
    private static final Set<String> PUBLIC = Set.of("/api/v1/status", "/api/v1/health", "/api/v1/leaderboards");
    private static final String PLAYERS = "/api/v1/players/";
    private static final String LINKS = "/api/v1/links/";
    private static final Set<String> PRIVATE = Set.of("/api/v1/telemetry", "/api/v1/catalog");
    private final ApiSettings settings;
    private final ApiToken token;
    private final Supplier<ApiSnapshot> snapshot;
    private final WebLinkCodes links;
    private final ApiRateLimit rate;
    private final NioEventLoopGroup boss = group("Aerborne-API-Accept");
    private final NioEventLoopGroup worker = group("Aerborne-API-HTTP");
    private final Map<ChannelId, Channel> clients = new ConcurrentHashMap<>();
    private final AtomicInteger connectionCount = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Channel server;

    ApiHttpListener(ApiSettings settings, ApiToken token, Supplier<ApiSnapshot> snapshot) throws IOException {
        this(settings, token, snapshot, new WebLinkCodes(System::currentTimeMillis));
    }

    ApiHttpListener(ApiSettings settings, ApiToken token, Supplier<ApiSnapshot> snapshot, WebLinkCodes links) throws IOException {
        this.settings = settings;
        this.token = token;
        this.snapshot = snapshot;
        this.links = links;
        rate = new ApiRateLimit(settings.globalPerMinute(), settings.perAddressPerMinute(), System::nanoTime);
        try {
            server = new ServerBootstrap().group(boss, worker).channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 32)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, false)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(16_384, 32_768))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override protected void initChannel(SocketChannel channel) {
                        int count = connectionCount.incrementAndGet();
                        channel.closeFuture().addListener(ignored -> {
                            clients.remove(channel.id());
                            connectionCount.decrementAndGet();
                        });
                        if (closed.get() || count > MAX_CONNECTIONS) { channel.close(); return; }
                        clients.put(channel.id(), channel);
                        // Absolute timeout cannot be kept alive by slowly trickling header bytes.
                        var timeout = channel.eventLoop().schedule(() -> { channel.close(); }, SOCKET_SECONDS, TimeUnit.SECONDS);
                        channel.closeFuture().addListener(ignored -> timeout.cancel(false));
                        channel.pipeline().addLast("request", new HttpRequestDecoder(new HttpDecoderConfig()
                            .setMaxInitialLineLength(2_048).setMaxHeaderSize(8_192).setMaxChunkSize(1_024)
                            .setInitialBufferSize(128).setAllowDuplicateContentLengths(false)
                            .setChunkedSupported(false).setValidateHeaders(true).setStrictLineParsing(true)));
                        channel.pipeline().addLast("response", new HttpResponseEncoder());
                        channel.pipeline().addLast("write-timeout", new WriteTimeoutHandler(2));
                        channel.pipeline().addLast("routes", new RequestHandler());
                    }
                }).bind(new InetSocketAddress(settings.bind(), settings.port())).syncUninterruptibly().channel();
        } catch (RuntimeException failure) {
            boss.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            worker.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            throw new IOException("The web API could not bind its configured address and port", failure);
        }
    }

    /** The pinned Netty constructor allows explicit task queues without global JVM settings. */
    @SuppressWarnings("deprecation")
    private static NioEventLoopGroup group(String name) {
        return new NioEventLoopGroup(1, new ThreadPerTaskExecutor(new DefaultThreadFactory(name, true)),
            DefaultEventExecutorChooserFactory.INSTANCE, SelectorProvider.provider(), DefaultSelectStrategyFactory.INSTANCE,
            RejectedExecutionHandlers.reject(), ignored -> new ArrayBlockingQueue<>(256),
            ignored -> new ArrayBlockingQueue<>(256));
    }

    int port() { return ((InetSocketAddress) server.localAddress()).getPort(); }
    InetAddress bindAddress() { return ((InetSocketAddress) server.localAddress()).getAddress(); }
    int connections() { return clients.size(); }

    private final class RequestHandler extends SimpleChannelInboundHandler<HttpObject> {
        private boolean answered;

        @Override protected void channelRead0(ChannelHandlerContext context, HttpObject message) {
            if (answered || !(message instanceof HttpRequest request)) return;
            answered = true;
            context.channel().config().setAutoRead(false);
            if (!request.decoderResult().isSuccess()) { send(context, 400, error("invalid_request"), null); return; }
            var headers = request.headers();
            if (headers.size() > 32 || !hostAllowed(context, headers.getAll(HttpHeaderNames.HOST))) {
                send(context, 403, error("forbidden_host"), null); return;
            }
            String address = ((InetSocketAddress) context.channel().remoteAddress()).getAddress().getHostAddress();
            if (!rate.acquire(address)) { send(context, 429, error("rate_limited"), null); return; }
            // Reject bodies without reading or buffering them. Duplicate length/transfer headers cannot smuggle requests.
            List<String> lengths = headers.getAll(HttpHeaderNames.CONTENT_LENGTH);
            if (headers.contains(HttpHeaderNames.TRANSFER_ENCODING) || lengths.size() > 1
                || !lengths.isEmpty() && !lengths.getFirst().equals("0") || headers.contains(HttpHeaderNames.EXPECT)) {
                send(context, 413, error("request_body_not_supported"), null); return;
            }
            String path = request.uri();
            if (path.length() > 128 || path.indexOf('?') >= 0 || path.indexOf('#') >= 0 || path.indexOf('%') >= 0) {
                send(context, 400, error("invalid_path"), null); return;
            }
            boolean player = path.startsWith(PLAYERS) && path.substring(PLAYERS.length()).matches("[A-Za-z0-9_]{3,16}");
            boolean link = path.startsWith(LINKS) && WebLinkCodes.valid(path.substring(LINKS.length()));
            boolean isPrivate = PRIVATE.contains(path) || link;
            if (!PUBLIC.contains(path) && !isPrivate && !player) { send(context, 404, error("not_found"), null); return; }
            List<String> origins = headers.getAll(HttpHeaderNames.ORIGIN);
            if (origins.size() > 1 || !origins.isEmpty() && !settings.corsOrigins().contains(origins.getFirst())) {
                send(context, 403, error("forbidden_origin"), null); return;
            }
            String origin = origins.isEmpty() ? null : origins.getFirst();
            if (request.method().equals(HttpMethod.OPTIONS)) {
                List<String> methods = headers.getAll(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD);
                List<String> requested = headers.getAll(HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS);
                if (origin == null || !methods.equals(List.of("GET")) || requested.size() > 1
                    || !requested.isEmpty() && !requested.getFirst().equalsIgnoreCase("authorization")) {
                    send(context, 403, error("preflight_refused"), null); return;
                }
                send(context, 204, "", origin); return;
            }
            if (!request.method().equals(HttpMethod.GET)) { send(context, 405, error("read_only"), origin); return; }
            if (isPrivate) {
                List<String> authorization = headers.getAll(HttpHeaderNames.AUTHORIZATION);
                if (authorization.size() != 1 || !token.verify(authorization.getFirst())) {
                    send(context, 401, error("authentication_required"), origin); return;
                }
            }
            if (link) {
                // Link codes live outside the snapshot so a code works the moment the player asks for it.
                var found = links.find(path.substring(LINKS.length()));
                if (found.isEmpty()) { send(context, 404, error("link_not_found"), origin); return; }
                var body = new com.google.gson.JsonObject();
                body.addProperty("apiVersion", 1);
                body.addProperty("accountId", found.get().accountId());
                body.addProperty("playerId", found.get().playerId().toString());
                body.addProperty("name", found.get().name());
                body.addProperty("issuedAt", java.time.Instant.ofEpochMilli(found.get().issuedAtMillis()).toString());
                body.addProperty("expiresAt", java.time.Instant.ofEpochMilli(found.get().expiresAtMillis()).toString());
                send(context, 200, body.toString(), origin); return;
            }
            ApiSnapshot current = snapshot.get();
            if (current == null || current.stale(System.currentTimeMillis(), settings.snapshotSeconds())) {
                send(context, 503, "{\"ready\":false,\"error\":\"snapshot_unavailable\"}", origin); return;
            }
            if (player) {
                String profile = current.players().get(path.substring(PLAYERS.length()).toLowerCase(Locale.ROOT));
                if (profile == null) { send(context, 404, error("player_not_found"), origin); return; }
                send(context, 200, profile, origin); return;
            }
            String json = switch (path) {
                case "/api/v1/health" -> "{\"ready\":true}";
                case "/api/v1/status" -> current.status();
                case "/api/v1/telemetry" -> current.telemetry();
                case "/api/v1/catalog" -> current.catalog();
                case "/api/v1/leaderboards" -> current.leaderboards();
                default -> throw new IllegalStateException("Unsupported API route");
            };
            send(context, 200, json, origin);
        }

        @Override public void exceptionCaught(ChannelHandlerContext context, Throwable failure) {
            // Never log raw requests, headers, tokens or addresses on decoding/connection failures.
            context.close();
        }
    }

    private boolean hostAllowed(ChannelHandlerContext context, List<String> hosts) {
        if (hosts.size() != 1) return false;
        String host = hosts.getFirst().toLowerCase(Locale.ROOT);
        InetSocketAddress local = (InetSocketAddress) context.channel().localAddress();
        InetSocketAddress remote = (InetSocketAddress) context.channel().remoteAddress();
        if (settings.bind().isLoopbackAddress() && !remote.getAddress().isLoopbackAddress()) return false;
        return host.equals(local.getAddress().getHostAddress() + ":" + port())
            || settings.allowedHosts().contains(host)
            || remote.getAddress().isLoopbackAddress() && host.equals("localhost:" + port());
    }

    private static String error(String code) { return "{\"error\":\"" + code + "\"}"; }

    private static void send(ChannelHandlerContext context, int status, String json, String origin) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(status),
            Unpooled.wrappedBuffer(bytes));
        var headers = response.headers();
        headers.set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        headers.setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        headers.set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        headers.set(HttpHeaderNames.VARY, "Origin");
        if (origin != null) {
            headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            if (status == 204) {
                headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET");
                headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization");
            }
        }
        if (status == 401) headers.set(HttpHeaderNames.WWW_AUTHENTICATE, "Bearer realm=\"Aerborne integrations\"");
        if (status == 405) headers.set(HttpHeaderNames.ALLOW, "GET, OPTIONS");
        if (status == 429) headers.set(HttpHeaderNames.RETRY_AFTER, "60");
        context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        server.close();
        clients.values().forEach(Channel::close);
        boss.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        worker.shutdownGracefully(0, 1, TimeUnit.SECONDS);
    }
}
