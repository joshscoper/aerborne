CREATE TABLE IF NOT EXISTS nerdsmp_schema_history (
    version INTEGER PRIMARY KEY,
    description TEXT NOT NULL,
    installed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS accounts (
    account_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS platform_identities (
    platform VARCHAR(16) NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(account_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (platform, external_id)
);

CREATE TABLE IF NOT EXISTS economy_accounts (
    account_id UUID PRIMARY KEY REFERENCES accounts(account_id),
    balance BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS economy_ledger (
    ledger_id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    source_account UUID NULL REFERENCES accounts(account_id),
    destination_account UUID NULL REFERENCES accounts(account_id),
    amount BIGINT NOT NULL CHECK (amount > 0),
    reason_code VARCHAR(64) NOT NULL,
    reason_detail VARCHAR(256) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS economy_ledger_source_idx ON economy_ledger(source_account, created_at DESC);
CREATE INDEX IF NOT EXISTS economy_ledger_destination_idx ON economy_ledger(destination_account, created_at DESC);

CREATE TABLE IF NOT EXISTS entitlements (
    transaction_id VARCHAR(128) PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(account_id),
    product_id VARCHAR(96) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NULL,
    revoked_at TIMESTAMPTZ NULL,
    revoke_reason VARCHAR(256) NULL
);

CREATE INDEX IF NOT EXISTS entitlements_account_idx ON entitlements(account_id, granted_at DESC);

INSERT INTO nerdsmp_schema_history(version, description)
VALUES (1, 'foundation')
ON CONFLICT (version) DO NOTHING;

