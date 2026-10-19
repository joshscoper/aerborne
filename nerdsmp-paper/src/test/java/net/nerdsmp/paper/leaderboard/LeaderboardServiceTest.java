package net.nerdsmp.paper.leaderboard;

import net.nerdsmp.api.model.AccountId;
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
        assertEquals(6,LeaderboardService.Board.values().length);
    }
}
