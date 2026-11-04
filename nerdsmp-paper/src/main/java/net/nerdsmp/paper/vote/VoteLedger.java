package net.nerdsmp.paper.vote;

import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/**
 * A player's voting record, kept in their player data under {@code votes.}: when each site last counted a vote,
 * today's paid votes, the day streak and the running total. Decides what each new vote pays.
 */
final class VoteLedger {
    enum Status { PAID, REPEAT, DAILY_LIMIT }

    record Rules(long marksPerVote, long streakBonus, int streakCap, int boosterEvery, Duration repeat, int maxPerDay) {}

    record Result(Status status, long marks, int streak, int total, boolean booster) {}

    private VoteLedger() {}

    /** A site's name as a data key: lower case, letters, digits and dashes. */
    static String key(String service) {
        String key = service.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (key.length() > 48) key = key.substring(0, 48);
        return key.isEmpty() ? "site" : key;
    }

    static Result record(ConfigurationSection data, String service, long now, ZoneId zone, Rules rules) {
        String site = "votes.sites." + key(service);
        long last = data.getLong(site, 0L);
        if (last > 0 && now >= last && now - last < rules.repeat().toMillis()) {
            return new Result(Status.REPEAT, 0, streak(data, now, zone), data.getInt("votes.total"), false);
        }
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        String day = today.toString();
        int paidToday = day.equals(data.getString("votes.day")) ? data.getInt("votes.paid-today") : 0;
        if (paidToday >= rules.maxPerDay()) return new Result(Status.DAILY_LIMIT, 0, streak(data, now, zone), data.getInt("votes.total"), false);
        String streakDay = data.getString("votes.streak-day");
        int streak = data.getInt("votes.streak");
        if (!day.equals(streakDay)) streak = today.minusDays(1).toString().equals(streakDay) ? streak + 1 : 1;
        int total = data.getInt("votes.total") + 1;
        long marks = rules.marksPerVote() + rules.streakBonus() * (Math.min(streak, rules.streakCap()) - 1);
        data.set(site, now);
        data.set("votes.day", day);
        data.set("votes.paid-today", paidToday + 1);
        data.set("votes.streak-day", day);
        data.set("votes.streak", streak);
        data.set("votes.total", total);
        return new Result(Status.PAID, marks, streak, total, rules.boosterEvery() > 0 && total % rules.boosterEvery() == 0);
    }

    /** The streak still alive now: one that last voted today or yesterday. */
    static int streak(ConfigurationSection data, long now, ZoneId zone) {
        String streakDay = data.getString("votes.streak-day");
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        return today.toString().equals(streakDay) || today.minusDays(1).toString().equals(streakDay) ? data.getInt("votes.streak") : 0;
    }

    /** When the player can next vote on a site, or 0 if they can now. Sites allow a vote a day. */
    static long nextVote(ConfigurationSection data, String service, long now) {
        long last = data.getLong("votes.sites." + key(service), 0L);
        long next = last + Duration.ofDays(1).toMillis();
        return last > 0 && next > now ? next : 0L;
    }
}
