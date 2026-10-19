package net.nerdsmp.paper.storage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProgressionMigrationContractTest {
    @Test
    void migrationContainsSeasonalSkillsMasteriesAndActivityIdempotency() throws IOException {
        String migration = Files.readString(Path.of(
            "src", "main", "resources", "db", "migration", "V002__progression.sql"
        ));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS progression_accounts"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS skill_progress"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS skill_masteries"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS progression_activities"));
        assertTrue(migration.contains("PRIMARY KEY (account_id, season_id, skill, activity_fingerprint)"));
        assertTrue(migration.contains("VALUES (2, 'seasonal progression')"));

        String talents = Files.readString(Path.of(
            "src", "main", "resources", "db", "migration", "V003__skill_talents.sql"
        ));
        assertTrue(talents.contains("CREATE TABLE IF NOT EXISTS skill_talents"));
        assertTrue(talents.contains("PRIMARY KEY (account_id, season_id, talent_id)"));
        assertTrue(talents.contains("VALUES (3, 'skill talent trees')"));

        String reputation = Files.readString(Path.of(
            "src", "main", "resources", "db", "migration", "V004__npc_reputation.sql"
        ));
        assertTrue(reputation.contains("CREATE TABLE IF NOT EXISTS reputation_accounts"));
        assertTrue(reputation.contains("CREATE TABLE IF NOT EXISTS reputation_ledger"));
        assertTrue(reputation.contains("idempotency_key VARCHAR(160) NOT NULL UNIQUE"));
        assertTrue(reputation.contains("VALUES (4, 'npc faction reputation ledger')"));
    }
}
