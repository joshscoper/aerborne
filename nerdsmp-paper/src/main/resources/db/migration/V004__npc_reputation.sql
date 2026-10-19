CREATE TABLE IF NOT EXISTS reputation_accounts (
    account_id UUID NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE,
    season_id VARCHAR(64) NOT NULL,
    faction_id VARCHAR(64) NOT NULL,
    points INTEGER NOT NULL DEFAULT 0 CHECK (points BETWEEN -1000 AND 1000),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, season_id, faction_id)
);

CREATE TABLE IF NOT EXISTS reputation_ledger (
    ledger_id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    account_id UUID NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE,
    season_id VARCHAR(64) NOT NULL,
    faction_id VARCHAR(64) NOT NULL,
    requested_amount INTEGER NOT NULL CHECK (requested_amount <> 0),
    applied_amount INTEGER NOT NULL,
    resulting_points INTEGER NOT NULL CHECK (resulting_points BETWEEN -1000 AND 1000),
    reason_code VARCHAR(64) NOT NULL,
    reason_detail VARCHAR(256) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS reputation_ledger_account_idx
    ON reputation_ledger(account_id, season_id, created_at DESC);

INSERT INTO nerdsmp_schema_history(version, description)
VALUES (4, 'npc faction reputation ledger')
ON CONFLICT (version) DO NOTHING;
