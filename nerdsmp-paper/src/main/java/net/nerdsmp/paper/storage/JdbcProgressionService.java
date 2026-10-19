package net.nerdsmp.paper.storage;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.model.SkillTreeCatalog;
import net.nerdsmp.api.service.ProgressionService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public final class JdbcProgressionService implements ProgressionService {
    private final DataSource dataSource;
    private final Executor executor;
    private final String seasonId;
    private final Duration respecCooldown;

    public JdbcProgressionService(
        DataSource dataSource,
        Executor executor,
        String seasonId,
        Duration respecCooldown
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.seasonId = Objects.requireNonNull(seasonId, "seasonId");
        this.respecCooldown = Objects.requireNonNull(respecCooldown, "respecCooldown");
        if (seasonId.isBlank() || seasonId.length() > 64) {
            throw new IllegalArgumentException("seasonId must contain 1-64 characters");
        }
        if (respecCooldown.isNegative()) {
            throw new IllegalArgumentException("respecCooldown cannot be negative");
        }
    }

    @Override
    public CompletionStage<ProgressionSnapshot> snapshot(AccountId accountId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                ensureAccount(connection, accountId);
                EnumMap<Skill, SkillProgress> skills = emptySkills();
                try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT skill, experience, level FROM skill_progress WHERE account_id = ? AND season_id = ?"
                )) {
                    statement.setObject(1, accountId.value());
                    statement.setString(2, seasonId);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            Skill skill = Skill.valueOf(result.getString("skill"));
                            skills.put(skill, new SkillProgress(result.getLong("experience"), result.getInt("level")));
                        }
                    }
                }

                EnumSet<Skill> masteries = EnumSet.noneOf(Skill.class);
                try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT skill FROM skill_masteries WHERE account_id = ? AND season_id = ?"
                )) {
                    statement.setObject(1, accountId.value());
                    statement.setString(2, seasonId);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            masteries.add(Skill.valueOf(result.getString("skill")));
                        }
                    }
                }
                return new ProgressionSnapshot(skills, masteries, readTalents(connection, accountId),
                    nextRespecAt(connection, accountId));
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    @Override
    public CompletionStage<AwardResult> awardExperience(
        AccountId accountId,
        Skill skill,
        long experience,
        String activityFingerprint
    ) {
        if (experience <= 0L || activityFingerprint.isBlank() || activityFingerprint.length() > 256) {
            return CompletableFuture.completedFuture(new AwardResult(false, new SkillProgress(0L, 0), false));
        }
        return CompletableFuture.supplyAsync(() -> inTransaction(connection -> {
            ensureAccount(connection, accountId);
            lockAccount(connection, accountId);
            SkillProgress current = readProgress(connection, accountId, skill, true);
            try (PreparedStatement activity = connection.prepareStatement(
                "INSERT INTO progression_activities(account_id, season_id, skill, activity_fingerprint) "
                    + "VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING"
            )) {
                activity.setObject(1, accountId.value());
                activity.setString(2, seasonId);
                activity.setString(3, skill.name());
                activity.setString(4, activityFingerprint);
                if (activity.executeUpdate() == 0) {
                    return new AwardResult(false, current, false);
                }
            }

            long cap = ProgressionService.experienceForLevel(MAX_LEVEL);
            long nextExperience = Math.min(cap, current.experience() + Math.min(experience, cap));
            int nextLevel = ProgressionService.levelForExperience(nextExperience);
            try (PreparedStatement update = connection.prepareStatement(
                "INSERT INTO skill_progress(account_id, season_id, skill, experience, level) VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT (account_id, season_id, skill) DO UPDATE SET "
                    + "experience = EXCLUDED.experience, level = EXCLUDED.level, updated_at = now()"
            )) {
                update.setObject(1, accountId.value());
                update.setString(2, seasonId);
                update.setString(3, skill.name());
                update.setLong(4, nextExperience);
                update.setInt(5, nextLevel);
                update.executeUpdate();
            }
            SkillProgress next = new SkillProgress(nextExperience, nextLevel);
            return new AwardResult(true, next, nextLevel > current.level());
        }), executor);
    }

    @Override
    public CompletionStage<MasteryResult> selectMastery(AccountId accountId, Skill skill) {
        return CompletableFuture.supplyAsync(() -> inTransaction(connection -> {
            ensureAccount(connection, accountId);
            lockAccount(connection, accountId);
            try (PreparedStatement existing = connection.prepareStatement(
                "SELECT 1 FROM skill_masteries WHERE account_id = ? AND season_id = ? AND skill = ?"
            )) {
                existing.setObject(1, accountId.value());
                existing.setString(2, seasonId);
                existing.setString(3, skill.name());
                try (ResultSet result = existing.executeQuery()) {
                    if (result.next()) {
                        return MasteryResult.ALREADY_SELECTED;
                    }
                }
            }
            if (readProgress(connection, accountId, skill, true).level() < MASTERY_LEVEL) {
                return MasteryResult.LEVEL_TOO_LOW;
            }
            try (PreparedStatement count = connection.prepareStatement(
                "SELECT count(*) FROM skill_masteries WHERE account_id = ? AND season_id = ?"
            )) {
                count.setObject(1, accountId.value());
                count.setString(2, seasonId);
                try (ResultSet result = count.executeQuery()) {
                    result.next();
                    if (result.getInt(1) >= MAX_MASTERIES) {
                        return MasteryResult.LIMIT_REACHED;
                    }
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO skill_masteries(account_id, season_id, skill) VALUES (?, ?, ?)"
            )) {
                insert.setObject(1, accountId.value());
                insert.setString(2, seasonId);
                insert.setString(3, skill.name());
                insert.executeUpdate();
            }
            return MasteryResult.SELECTED;
        }), executor);
    }

    @Override
    public CompletionStage<MasteryResult> removeMastery(AccountId accountId, Skill skill, Instant now) {
        return CompletableFuture.supplyAsync(() -> inTransaction(connection -> {
            ensureAccount(connection, accountId);
            lockAccount(connection, accountId);
            Instant nextRespec = nextRespecAt(connection, accountId);
            if (nextRespec.isAfter(now)) {
                return MasteryResult.RESPEC_ON_COOLDOWN;
            }
            try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM skill_masteries WHERE account_id = ? AND season_id = ? AND skill = ?"
            )) {
                delete.setObject(1, accountId.value());
                delete.setString(2, seasonId);
                delete.setString(3, skill.name());
                if (delete.executeUpdate() == 0) {
                    return MasteryResult.NOT_SELECTED;
                }
            }
            try (PreparedStatement update = connection.prepareStatement(
                "UPDATE progression_accounts SET next_respec_at = ? WHERE account_id = ? AND season_id = ?"
            )) {
                update.setTimestamp(1, Timestamp.from(now.plus(respecCooldown)));
                update.setObject(2, accountId.value());
                update.setString(3, seasonId);
                update.executeUpdate();
            }
            return MasteryResult.REMOVED;
        }), executor);
    }

    @Override
    public CompletionStage<TalentResult> unlockTalent(AccountId accountId, String talentId) {
        SkillTreeCatalog.TalentNode node = SkillTreeCatalog.node(talentId).orElse(null);
        if (node == null) {
            return CompletableFuture.completedFuture(TalentResult.UNKNOWN_TALENT);
        }
        return CompletableFuture.supplyAsync(() -> inTransaction(connection -> {
            ensureAccount(connection, accountId);
            lockAccount(connection, accountId);
            Set<String> talents = readTalents(connection, accountId);
            if (talents.contains(node.id())) {
                return TalentResult.ALREADY_UNLOCKED;
            }
            SkillProgress progress = readProgress(connection, accountId, node.skill(), false);
            if (progress.level() < node.requiredLevel()) {
                return TalentResult.LEVEL_TOO_LOW;
            }
            if (node.previousId() != null && !talents.contains(node.previousId())) {
                return TalentResult.PREREQUISITE_MISSING;
            }
            int earned = SkillTreeCatalog.earnedPoints(progress.level());
            if (SkillTreeCatalog.spentPoints(talents, node.skill()) >= earned) {
                return TalentResult.NO_POINTS_AVAILABLE;
            }
            try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO skill_talents(account_id, season_id, talent_id) VALUES (?, ?, ?)"
            )) {
                insert.setObject(1, accountId.value());
                insert.setString(2, seasonId);
                insert.setString(3, node.id());
                insert.executeUpdate();
            }
            return TalentResult.UNLOCKED;
        }), executor);
    }

    private void ensureAccount(Connection connection, AccountId accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO progression_accounts(account_id, season_id) VALUES (?, ?) ON CONFLICT DO NOTHING"
        )) {
            statement.setObject(1, accountId.value());
            statement.setString(2, seasonId);
            statement.executeUpdate();
        }
    }

    private void lockAccount(Connection connection, AccountId accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM progression_accounts WHERE account_id = ? AND season_id = ? FOR UPDATE"
        )) {
            statement.setObject(1, accountId.value());
            statement.setString(2, seasonId);
            statement.executeQuery();
        }
    }

    private SkillProgress readProgress(Connection connection, AccountId accountId, Skill skill, boolean lock)
        throws SQLException {
        String sql = "SELECT experience, level FROM skill_progress WHERE account_id = ? AND season_id = ? AND skill = ?"
            + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId.value());
            statement.setString(2, seasonId);
            statement.setString(3, skill.name());
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                    ? new SkillProgress(result.getLong("experience"), result.getInt("level"))
                    : new SkillProgress(0L, 0);
            }
        }
    }

    private Instant nextRespecAt(Connection connection, AccountId accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT next_respec_at FROM progression_accounts WHERE account_id = ? AND season_id = ?"
        )) {
            statement.setObject(1, accountId.value());
            statement.setString(2, seasonId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getTimestamp(1).toInstant() : Instant.EPOCH;
            }
        }
    }

    private Set<String> readTalents(Connection connection, AccountId accountId) throws SQLException {
        Set<String> talents = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT talent_id FROM skill_talents WHERE account_id = ? AND season_id = ?"
        )) {
            statement.setObject(1, accountId.value());
            statement.setString(2, seasonId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    talents.add(result.getString("talent_id"));
                }
            }
        }
        return Set.copyOf(talents);
    }

    private EnumMap<Skill, SkillProgress> emptySkills() {
        EnumMap<Skill, SkillProgress> skills = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            skills.put(skill, new SkillProgress(0L, 0));
        }
        return skills;
    }

    private <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws Exception;
    }
}
