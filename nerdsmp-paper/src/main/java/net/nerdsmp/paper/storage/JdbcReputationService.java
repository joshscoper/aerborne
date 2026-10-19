package net.nerdsmp.paper.storage;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.FactionId;
import net.nerdsmp.api.service.ReputationService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public final class JdbcReputationService implements ReputationService {
    private final DataSource dataSource;
    private final Executor executor;
    private final String seasonId;

    public JdbcReputationService(DataSource dataSource, Executor executor, String seasonId) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.seasonId = Objects.requireNonNull(seasonId, "seasonId");
    }

    @Override
    public CompletionStage<Integer> value(AccountId accountId, FactionId factionId) {
        return supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                ensureAccount(connection, accountId);
                return current(connection, accountId, factionId, false);
            }
        });
    }

    @Override
    public CompletionStage<Map<FactionId, Integer>> standings(AccountId accountId) {
        return supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                ensureAccount(connection, accountId);
                Map<FactionId, Integer> result = new TreeMap<>();
                try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT faction_id, points FROM reputation_accounts WHERE account_id = ? AND season_id = ?"
                )) {
                    statement.setObject(1, accountId.value());
                    statement.setString(2, seasonId);
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) {
                            result.put(new FactionId(rows.getString(1)), rows.getInt(2));
                        }
                    }
                }
                return Map.copyOf(result);
            }
        });
    }

    @Override
    public CompletionStage<AdjustmentResult> adjust(
        AccountId accountId,
        FactionId factionId,
        int amount,
        ReputationReason reason,
        String idempotencyKey
    ) {
        if (amount == 0 || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 160) {
            return value(accountId, factionId).thenApply(current -> new AdjustmentResult(
                AdjustmentResult.Status.INVALID_AMOUNT, current, current, 0
            ));
        }
        return transaction(connection -> {
            ensureAccount(connection, accountId);
            ensureStanding(connection, accountId, factionId);
            int previous = current(connection, accountId, factionId, true);
            try (PreparedStatement existing = connection.prepareStatement(
                "SELECT 1 FROM reputation_ledger WHERE idempotency_key = ?"
            )) {
                existing.setString(1, idempotencyKey);
                try (ResultSet row = existing.executeQuery()) {
                    if (row.next()) {
                        return new AdjustmentResult(AdjustmentResult.Status.DUPLICATE, previous, previous, 0);
                    }
                }
            }
            int next = clamp((long) previous + amount);
            int applied = next - previous;
            try (PreparedStatement ledger = connection.prepareStatement("""
                INSERT INTO reputation_ledger(
                    idempotency_key, account_id, season_id, faction_id,
                    requested_amount, applied_amount, resulting_points, reason_code, reason_detail
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
                ledger.setString(1, idempotencyKey);
                ledger.setObject(2, accountId.value());
                ledger.setString(3, seasonId);
                ledger.setString(4, factionId.value());
                ledger.setInt(5, amount);
                ledger.setInt(6, applied);
                ledger.setInt(7, next);
                ledger.setString(8, reason.code());
                ledger.setString(9, reason.detail());
                ledger.executeUpdate();
            }
            try (PreparedStatement update = connection.prepareStatement(
                "UPDATE reputation_accounts SET points = ?, updated_at = now() "
                    + "WHERE account_id = ? AND season_id = ? AND faction_id = ?"
            )) {
                update.setInt(1, next);
                update.setObject(2, accountId.value());
                update.setString(3, seasonId);
                update.setString(4, factionId.value());
                update.executeUpdate();
            }
            return new AdjustmentResult(AdjustmentResult.Status.APPLIED, previous, next, applied);
        });
    }

    private void ensureAccount(Connection connection, AccountId accountId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO accounts(account_id) VALUES (?) ON CONFLICT (account_id) DO NOTHING"
        )) {
            statement.setObject(1, accountId.value());
            statement.executeUpdate();
        }
    }

    private void ensureStanding(Connection connection, AccountId accountId, FactionId factionId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO reputation_accounts(account_id, season_id, faction_id, points) "
                + "VALUES (?, ?, ?, 0) ON CONFLICT DO NOTHING"
        )) {
            statement.setObject(1, accountId.value());
            statement.setString(2, seasonId);
            statement.setString(3, factionId.value());
            statement.executeUpdate();
        }
    }

    private int current(Connection connection, AccountId accountId, FactionId factionId, boolean lock) throws Exception {
        String sql = "SELECT points FROM reputation_accounts WHERE account_id = ? AND season_id = ? AND faction_id = ?"
            + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId.value());
            statement.setString(2, seasonId);
            statement.setString(3, factionId.value());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private int clamp(long value) {
        return (int) Math.max(MINIMUM, Math.min(MAXIMUM, value));
    }

    private <T> CompletionStage<T> transaction(SqlWork<T> work) {
        return supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    T value = work.apply(connection);
                    connection.commit();
                    return value;
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        });
    }

    private <T> CompletionStage<T> supply(SqlWorkWithoutConnection<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.apply();
            } catch (Exception exception) {
                throw new JdbcEconomyService.DatabaseException("PostgreSQL reputation operation failed", exception);
            }
        }, executor);
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws Exception;
    }

    @FunctionalInterface
    private interface SqlWorkWithoutConnection<T> {
        T apply() throws Exception;
    }
}
