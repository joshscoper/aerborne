package net.nerdsmp.paper.leaderboard;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ProgressionService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LeaderboardServiceTest {
    @Test void allBoardsSortDescendingAndResolveTiesDeterministicallyIncludingOfflineAccounts() {
        var zed=new LeaderboardService.Entry(AccountId.random(),"Zed",Map.of(LeaderboardService.Board.MARKS,10L,LeaderboardService.Board.COOKING,50L));
        var amy=new LeaderboardService.Entry(AccountId.random(),"Amy",Map.of(LeaderboardService.Board.MARKS,10L));
        var high=new LeaderboardService.Entry(AccountId.random(),"Offline",Map.of(LeaderboardService.Board.MARKS,Long.MAX_VALUE));
        assertEquals(List.of(high,amy,zed),LeaderboardService.rank(List.of(zed,amy,high),LeaderboardService.Board.MARKS));
        assertEquals(zed,LeaderboardService.rank(List.of(zed,amy,high),LeaderboardService.Board.COOKING).getFirst());
        assertThrows(UnsupportedOperationException.class,()->LeaderboardService.rank(List.of(zed),LeaderboardService.Board.MARKS).clear());
    }
    @Test void absentScoresAreZeroAndNegativeScoresAreRejected() {
        var entry=new LeaderboardService.Entry(AccountId.random(),"New",Map.of());assertEquals(0,entry.score(LeaderboardService.Board.ISLAND));
        assertThrows(IllegalArgumentException.class,()->new LeaderboardService.Entry(AccountId.random(),"Bad",Map.of(LeaderboardService.Board.MARKS,-1L)));
        assertEquals(16,LeaderboardService.Board.values().length);
    }
    @Test void eachSkillRanksExactXpIncludingPlayersAtTheSameLevel() {
        var progress = new EnumMap<Skill, ProgressionService.SkillProgress>(Skill.class);
        for (Skill skill : Skill.values()) progress.put(skill, new ProgressionService.SkillProgress(10_010L + skill.ordinal(), 10));
        var snapshot = new ProgressionService.ProgressionSnapshot(progress, Set.of(), Set.of(), java.time.Instant.EPOCH);
        var scores = LeaderboardService.progressionScores(snapshot);
        var covered = EnumSet.noneOf(Skill.class);
        for (var board : LeaderboardService.Board.values()) board.skill().ifPresent(skill -> {
            assertTrue(covered.add(skill), "Only one board per skill");
            assertEquals(progress.get(skill).experience(), scores.get(board));
            var lower = new LeaderboardService.Entry(AccountId.random(), "Amy", Map.of(board, scores.get(board) - 1));
            var higher = new LeaderboardService.Entry(AccountId.random(), "Zed", Map.of(board, scores.get(board)));
            assertEquals(higher, LeaderboardService.rank(List.of(lower, higher), board).getFirst());
        });
        assertEquals(EnumSet.allOf(Skill.class), covered);
        assertEquals(110L, scores.get(LeaderboardService.Board.SKILLS));
        var empty = LeaderboardService.progressionScores(ProgressionService.ProgressionSnapshot.empty());
        for (var board : LeaderboardService.Board.values()) if (board.skill().isPresent()) assertEquals(0L, empty.get(board));
    }
}
