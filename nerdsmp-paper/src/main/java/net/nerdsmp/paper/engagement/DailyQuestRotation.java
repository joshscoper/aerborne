package net.nerdsmp.paper.engagement;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class DailyQuestRotation {
    private DailyQuestRotation() {
    }

    static List<DailyQuestDefinition> select(
        List<DailyQuestDefinition> definitions,
        LocalDate date,
        int count,
        long seed
    ) {
        ArrayList<DailyQuestDefinition> shuffled = new ArrayList<>(definitions);
        Collections.shuffle(shuffled, new Random(date.toEpochDay() ^ seed));
        return List.copyOf(shuffled.subList(0, Math.min(Math.max(1, count), shuffled.size())));
    }
}
