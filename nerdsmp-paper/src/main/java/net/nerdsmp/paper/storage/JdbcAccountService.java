package net.nerdsmp.paper.storage;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.api.service.AccountService;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public final class JdbcAccountService implements AccountService {
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final DataSource dataSource;
    private final Executor executor;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, LinkCode> linkCodes = new ConcurrentHashMap<>();

    public JdbcAccountService(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public CompletionStage<AccountId> resolveOrCreate(PlatformIdentity identity) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Optional<AccountId> existing = find(connection, identity);
                    if (existing.isPresent()) {
                        connection.commit();
                        return existing.get();
                    }
                    AccountId created = AccountId.random();
                    try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO accounts(account_id) VALUES (?)")) {
                        statement.setObject(1, created.value());
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO platform_identities(platform, external_id, account_id)
                        VALUES (?, ?, ?)
                        ON CONFLICT (platform, external_id) DO NOTHING
                        """)) {
                        statement.setString(1, identity.platform().name());
                        statement.setString(2, identity.externalId());
                        statement.setObject(3, created.value());
                        statement.executeUpdate();
                    }
                    AccountId resolved = find(connection, identity).orElseThrow();
                    connection.commit();
                    return resolved;
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                }
            } catch (Exception exception) {
                throw new JdbcEconomyService.DatabaseException("Failed to resolve account", exception);
            }
        }, executor);
    }

    @Override
    public CompletionStage<Optional<AccountId>> find(PlatformIdentity identity) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return find(connection, identity);
            } catch (Exception exception) {
                throw new JdbcEconomyService.DatabaseException("Failed to find account", exception);
            }
        }, executor);
    }

    @Override
    public CompletionStage<String> createLinkCode(AccountId source, Duration lifetime) {
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofMinutes(30)) > 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid link code lifetime"));
        }
        String code;
        do {
            StringBuilder candidate = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                candidate.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
            }
            code = candidate.toString();
        } while (linkCodes.putIfAbsent(code, new LinkCode(source, Instant.now().plus(lifetime))) != null);
        return CompletableFuture.completedFuture(code);
    }

    @Override
    public CompletionStage<LinkResult> redeemLinkCode(
        PlatformIdentity target,
        String rawCode,
        boolean targetIsFresh
    ) {
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        LinkCode linkCode = linkCodes.remove(code);
        if (linkCode == null || linkCode.expiresAt.isBefore(Instant.now())) {
            return CompletableFuture.completedFuture(LinkResult.INVALID_OR_EXPIRED);
        }
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Optional<AccountId> existing = find(connection, target);
                    if (existing.filter(linkCode.source::equals).isPresent()) {
                        connection.commit();
                        return LinkResult.ALREADY_LINKED;
                    }
                    if (existing.isPresent() && !targetIsFresh) {
                        connection.commit();
                        linkCodes.put(code, linkCode);
                        return LinkResult.TARGET_HAS_PROGRESS;
                    }
                    try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO platform_identities(platform, external_id, account_id)
                        VALUES (?, ?, ?)
                        ON CONFLICT (platform, external_id)
                        DO UPDATE SET account_id = EXCLUDED.account_id
                        """)) {
                        statement.setString(1, target.platform().name());
                        statement.setString(2, target.externalId());
                        statement.setObject(3, linkCode.source.value());
                        statement.executeUpdate();
                    }
                    connection.commit();
                    return LinkResult.LINKED;
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                }
            } catch (Exception exception) {
                throw new JdbcEconomyService.DatabaseException("Failed to redeem account link", exception);
            }
        }, executor);
    }

    private Optional<AccountId> find(Connection connection, PlatformIdentity identity) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT account_id FROM platform_identities WHERE platform = ? AND external_id = ?
            """)) {
            statement.setString(1, identity.platform().name());
            statement.setString(2, identity.externalId());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                    ? Optional.of(new AccountId(resultSet.getObject(1, java.util.UUID.class)))
                    : Optional.empty();
            }
        }
    }

    private record LinkCode(AccountId source, Instant expiresAt) {
    }
}

