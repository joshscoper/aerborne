package net.nerdsmp.paper.storage;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public final class JdbcEconomyService implements EconomyService {
    private final DataSource dataSource;
    private final Executor executor;

    public JdbcEconomyService(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public CompletionStage<Long> balance(AccountId accountId) {
        return supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                ensureAccount(connection, accountId);
                return currentBalance(connection, accountId, false);
            }
        });
    }

    @Override
    public CompletionStage<TransactionResult> credit(
        AccountId accountId,
        long marks,
        TransactionReason reason,
        String idempotencyKey
    ) {
        if (marks <= 0L) {
            return invalid(accountId);
        }
        return transaction(connection -> {
            ensureAccount(connection, accountId);
            long current = currentBalance(connection, accountId, true);
            if (!insertLedger(connection, idempotencyKey, null, accountId, marks, reason)) {
                return result(TransactionResult.Status.DUPLICATE, current);
            }
            long next = Math.addExact(current, marks);
            setBalance(connection, accountId, next);
            return result(TransactionResult.Status.APPLIED, next);
        });
    }

    @Override
    public CompletionStage<TransactionResult> debit(
        AccountId accountId,
        long marks,
        TransactionReason reason,
        String idempotencyKey
    ) {
        if (marks <= 0L) {
            return invalid(accountId);
        }
        return transaction(connection -> {
            ensureAccount(connection, accountId);
            long current = currentBalance(connection, accountId, true);
            if (ledgerExists(connection, idempotencyKey)) {
                return result(TransactionResult.Status.DUPLICATE, current);
            }
            if (current < marks) {
                return result(TransactionResult.Status.INSUFFICIENT_FUNDS, current);
            }
            if (!insertLedger(connection, idempotencyKey, accountId, null, marks, reason)) {
                return result(TransactionResult.Status.DUPLICATE, current);
            }
            long next = current - marks;
            setBalance(connection, accountId, next);
            return result(TransactionResult.Status.APPLIED, next);
        });
    }

    @Override
    public CompletionStage<TransactionResult> transfer(
        AccountId source,
        AccountId destination,
        long marks,
        TransactionReason reason,
        String idempotencyKey
    ) {
        if (marks <= 0L || source.equals(destination)) {
            return CompletableFuture.completedFuture(new TransactionResult(
                TransactionResult.Status.INVALID_AMOUNT,
                0L,
                OptionalLong.empty()
            ));
        }
        return transaction(connection -> {
            ensureAccount(connection, source);
            ensureAccount(connection, destination);
            List<AccountId> lockOrder = List.of(source, destination).stream()
                .sorted(Comparator.comparing(AccountId::toString))
                .toList();
            long firstBalance = currentBalance(connection, lockOrder.get(0), true);
            long secondBalance = currentBalance(connection, lockOrder.get(1), true);
            long sourceBalance = source.equals(lockOrder.get(0)) ? firstBalance : secondBalance;
            long destinationBalance = destination.equals(lockOrder.get(0)) ? firstBalance : secondBalance;

            if (ledgerExists(connection, idempotencyKey)) {
                return new TransactionResult(TransactionResult.Status.DUPLICATE, sourceBalance,
                    OptionalLong.of(destinationBalance));
            }
            if (sourceBalance < marks) {
                return new TransactionResult(TransactionResult.Status.INSUFFICIENT_FUNDS, sourceBalance,
                    OptionalLong.of(destinationBalance));
            }
            if (!insertLedger(connection, idempotencyKey, source, destination, marks, reason)) {
                return new TransactionResult(TransactionResult.Status.DUPLICATE, sourceBalance,
                    OptionalLong.of(destinationBalance));
            }

            long nextDestination = Math.addExact(destinationBalance, marks);
            setBalance(connection, source, sourceBalance - marks);
            setBalance(connection, destination, nextDestination);
            return new TransactionResult(TransactionResult.Status.APPLIED, sourceBalance - marks,
                OptionalLong.of(nextDestination));
        });
    }

    private CompletionStage<TransactionResult> invalid(AccountId accountId) {
        return balance(accountId).thenApply(current -> result(TransactionResult.Status.INVALID_AMOUNT, current));
    }

    private TransactionResult result(TransactionResult.Status status, long balance) {
        return new TransactionResult(status, balance, OptionalLong.empty());
    }

    private void ensureAccount(Connection connection, AccountId accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO accounts(account_id) VALUES (?) ON CONFLICT (account_id) DO NOTHING")) {
            statement.setObject(1, accountId.value());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO economy_accounts(account_id, balance) VALUES (?, 0) ON CONFLICT (account_id) DO NOTHING")) {
            statement.setObject(1, accountId.value());
            statement.executeUpdate();
        }
    }

    private long currentBalance(Connection connection, AccountId accountId, boolean lock) throws SQLException {
        String sql = "SELECT balance FROM economy_accounts WHERE account_id = ?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Economy account disappeared for " + accountId);
                }
                return resultSet.getLong(1);
            }
        }
    }

    private void setBalance(Connection connection, AccountId accountId, long balance) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE economy_accounts SET balance = ?, updated_at = now() WHERE account_id = ?")) {
            statement.setLong(1, balance);
            statement.setObject(2, accountId.value());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Failed to update economy balance for " + accountId);
            }
        }
    }

    private boolean ledgerExists(Connection connection, String idempotencyKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM economy_ledger WHERE idempotency_key = ?")) {
            statement.setString(1, idempotencyKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean insertLedger(
        Connection connection,
        String idempotencyKey,
        AccountId source,
        AccountId destination,
        long amount,
        TransactionReason reason
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO economy_ledger(
                idempotency_key, source_account, destination_account, amount, reason_code, reason_detail
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (idempotency_key) DO NOTHING
            """)) {
            statement.setString(1, idempotencyKey);
            statement.setObject(2, source == null ? null : source.value());
            statement.setObject(3, destination == null ? null : destination.value());
            statement.setLong(4, amount);
            statement.setString(5, reason.code());
            statement.setString(6, reason.detail());
            return statement.executeUpdate() == 1;
        }
    }

    private <T> CompletionStage<T> transaction(SqlFunction<T> function) {
        return supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    T result = function.apply(connection);
                    connection.commit();
                    return result;
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        });
    }

    private <T> CompletionStage<T> supply(SqlSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception exception) {
                throw new DatabaseException("PostgreSQL operation failed", exception);
            }
        }, executor);
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface SqlFunction<T> {
        T apply(Connection connection) throws Exception;
    }

    public static final class DatabaseException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DatabaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
