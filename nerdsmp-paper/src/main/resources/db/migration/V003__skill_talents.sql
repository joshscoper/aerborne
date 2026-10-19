CREATE TABLE IF NOT EXISTS skill_talents (
    account_id UUID NOT NULL,
    season_id VARCHAR(64) NOT NULL,
    talent_id VARCHAR(96) NOT NULL,
    selected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, season_id, talent_id),
    FOREIGN KEY (account_id, season_id)
        REFERENCES progression_accounts(account_id, season_id) ON DELETE CASCADE
);

INSERT INTO nerdsmp_schema_history(version, description)
VALUES (3, 'skill talent trees')
ON CONFLICT (version) DO NOTHING;
