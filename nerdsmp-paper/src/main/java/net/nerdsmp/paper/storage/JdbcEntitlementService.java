package net.nerdsmp.paper.storage;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EntitlementService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public final class JdbcEntitlementService implements EntitlementService {
    private final DataSource dataSource;
    private final Executor executor;

    public JdbcEntitlementService(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public CompletionStage<GrantResult> grant(
        AccountId accountId,
        String productId,
        String transactionId,
        Instant expiresAt
    ) {
        if (!productId.matches("[a-z0-9_.-]{1,96}")) {
            return CompletableFuture.completedFuture(GrantResult.INVALID_PRODUCT);
        }
        return supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                ensureAccount(connection, accountId);
                try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO entitlements(transaction_id, account_id, product_id, expires_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (transaction_id) DO NOTHING
                    """)) {
                    statement.setString(1, transactionId);
                    statement.setObject(2, accountId.value());
                    statement.setString(3, productId);
                    statement.setObject(4, expiresAt);
                    return statement.executeUpdate() == 1 ? GrantResult.APPLIED : GrantResult.DUPLICATE;
                }
            }
        });
    }

    @Override
    public CompletionStage<GrantResult> revoke(String transactionId, String reason) {
        return supply(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                     UPDATE entitlements
                     SET revoked_at = now(), revoke_reason = ?
                     WHERE transaction_id = ? AND revoked_at IS NULL
                     """)) {
                statement.setString(1, reason.substring(0, Math.min(reason.length(), 256)));
                statement.setString(2, transactionId);
                if (statement.executeUpdate() == 1) {
                    return GrantResult.REVOKED;
                }
                try (PreparedStatement lookup = connection.prepareStatement(
                    "SELECT revoked_at FROM entitlements WHERE transaction_id = ?")) {
                    lookup.setString(1, transactionId);
                    try (ResultSet resultSet = lookup.executeQuery()) {
                        return resultSet.next() ? GrantResult.DUPLICATE : GrantResult.NOT_FOUND;
                    }
                }
            }
        });
    }

    @Override
    public CompletionStage<Set<Entitlement>> activeEntitlements(AccountId accountId, Instant now) {
        return supply(() -> {
            Set<Entitlement> entitlements = new HashSet<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                     SELECT product_id, transaction_id, granted_at, expires_at
                     FROM entitlements
                     WHERE account_id = ?
                       AND revoked_at IS NULL
                       AND (expires_at IS NULL OR expires_at > ?)
                     """)) {
                statement.setObject(1, accountId.value());
                statement.setObject(2, now);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        entitlements.add(new Entitlement(
                            resultSet.getString(1),
                            resultSet.getString(2),
                            resultSet.getTimestamp(3).toInstant(),
                            resultSet.getTimestamp(4) == null ? null : resultSet.getTimestamp(4).toInstant()
                        ));
                    }
                }
            }
            return Set.copyOf(entitlements);
        });
    }

    private void ensureAccount(Connection connection, AccountId accountId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO accounts(account_id) VALUES (?) ON CONFLICT (account_id) DO NOTHING")) {
            statement.setObject(1, accountId.value());
            statement.executeUpdate();
        }
    }

    private <T> CompletionStage<T> supply(SqlSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception exception) {
                throw new JdbcEconomyService.DatabaseException("Entitlement database operation failed", exception);
            }
        }, executor);
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws Exception;
    }
}
