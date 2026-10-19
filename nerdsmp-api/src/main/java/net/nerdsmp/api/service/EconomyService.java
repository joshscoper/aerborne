package net.nerdsmp.api.service;

import net.nerdsmp.api.model.AccountId;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;

public interface EconomyService {
    CompletionStage<Long> balance(AccountId accountId);

    CompletionStage<TransactionResult> credit(
        AccountId accountId,
        long marks,
        TransactionReason reason,
        String idempotencyKey
    );

    CompletionStage<TransactionResult> debit(
        AccountId accountId,
        long marks,
        TransactionReason reason,
        String idempotencyKey
    );

    CompletionStage<TransactionResult> transfer(
        AccountId source,
        AccountId destination,
        long marks,
        TransactionReason reason,
        String idempotencyKey
    );

    record TransactionReason(String code, String detail) {
        public TransactionReason {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(detail, "detail");
            if (!code.matches("[a-z0-9_.-]{1,64}")) {
                throw new IllegalArgumentException("Invalid transaction reason code");
            }
            if (detail.length() > 256) {
                throw new IllegalArgumentException("Transaction reason detail is too long");
            }
        }
    }

    record TransactionResult(Status status, long primaryBalance, OptionalLong secondaryBalance) {
        public enum Status {
            APPLIED,
            DUPLICATE,
            INSUFFICIENT_FUNDS,
            INVALID_AMOUNT
        }

        public static TransactionResult applied(long balance) {
            return new TransactionResult(Status.APPLIED, balance, OptionalLong.empty());
        }
    }
}

