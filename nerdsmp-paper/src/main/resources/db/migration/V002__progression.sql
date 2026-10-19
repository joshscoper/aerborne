CREATE TABLE IF NOT EXISTS progression_accounts (
    account_id UUID NOT NULL REFERENCES accounts(account_id),
    season_id VARCHAR(64) NOT NULL,
    next_respec_at TIMESTAMPTZ NOT NULL DEFAULT to_timestamp(0),
    PRIMARY KEY (account_id, season_id)
);

CREATE TABLE IF NOT EXISTS skill_progress (
    account_id UUID NOT NULL,
    season_id VARCHAR(64) NOT NULL,
    skill VARCHAR(24) NOT NULL,
    experience BIGINT NOT NULL DEFAULT 0 CHECK (experience >= 0),
    level INTEGER NOT NULL DEFAULT 0 CHECK (level BETWEEN 0 AND 50),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, season_id, skill),
    FOREIGN KEY (account_id, season_id)
        REFERENCES progression_accounts(account_id, season_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS skill_masteries (
    account_id UUID NOT NULL,
    season_id VARCHAR(64) NOT NULL,
    skill VARCHAR(24) NOT NULL,
    selected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, season_id, skill),
    FOREIGN KEY (account_id, season_id)
        REFERENCES progression_accounts(account_id, season_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS progression_activities (
    account_id UUID NOT NULL,
    season_id VARCHAR(64) NOT NULL,
    skill VARCHAR(24) NOT NULL,
    activity_fingerprint VARCHAR(256) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, season_id, skill, activity_fingerprint),
    FOREIGN KEY (account_id, season_id)
        REFERENCES progression_accounts(account_id, season_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS progression_activities_created_idx
    ON progression_activities(created_at);

INSERT INTO nerdsmp_schema_history(version, description)
VALUES (2, 'seasonal progression')
ON CONFLICT (version) DO NOTHING;
