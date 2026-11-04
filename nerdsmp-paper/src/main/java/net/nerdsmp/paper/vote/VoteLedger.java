package net.nerdsmp.paper.vote;

import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A player's voting record, kept in their player data under {@code votes.}: when each site last counted a vote,
 * today's paid votes, the day streak and the running total. Decides what each new vote pays.
 */
final class VoteLedger {
    enum Status { PAID, REPEAT, DAILY_LIMIT }

    /**
     * {@code siteBonus} is added once for each different site already voted on that day, so the third site of the
     * day pays two bonuses more than the first; {@code allSitesBonus} is paid once a day for voting on every site in the menu.
     */
    record Rules(long marksPerVote, long streakBonus, int streakCap, int boosterEvery, Duration repeat, int maxPerDay,
                 long siteBonus, long allSitesBonus) {}

    /** {@code site} is which different site this was today, counting from 1; {@code allSites} says the all-sites bonus is in {@code marks}. */
    record Result(Status status, long marks, int streak, int total, boolean booster, int site, boolean allSites) {
        static Result unpaid(Status status, int streak, int total) { return new Result(status, 0, streak, total, false, 0, false); }
    }

    private VoteLedger() {}

    /** A site's name as a data key: lower case, letters, digits and dashes. */
    static String key(String service) {
        String key = service.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (key.length() > 48) key = key.substring(0, 48);
        return key.isEmpty() ? "site" : key;
    }

    /** Records one vote. {@code menuSites} are the keys of the sites in votes.yml, for the all-sites bonus. */
    static Result record(ConfigurationSection data, String service, long now, ZoneId zone, Rules rules, Set<String> menuSites) {
        String site = "votes.sites." + key(service);
        long last = data.getLong(site, 0L);
        if (last > 0 && now >= last && now - last < rules.repeat().toMillis()) {
            return Result.unpaid(Status.REPEAT, streak(data, now, zone), data.getInt("votes.total"));
        }
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        String day = today.toString();
        int paidToday = day.equals(data.getString("votes.day")) ? data.getInt("votes.paid-today") : 0;
        if (paidToday >= rules.maxPerDay()) return Result.unpaid(Status.DAILY_LIMIT, streak(data, now, zone), data.getInt("votes.total"));
        String streakDay = data.getString("votes.streak-day");
        int streak = data.getInt("votes.streak");
        if (!day.equals(streakDay)) streak = today.minusDays(1).toString().equals(streakDay) ? streak + 1 : 1;
        int total = data.getInt("votes.total") + 1;
        long marks = rules.marksPerVote() + rules.streakBonus() * (Math.min(streak, rules.streakCap()) - 1);
        // The day's sites in the order first voted on; a site voted on again later that day keeps its place.
        List<String> sites = day.equals(data.getString("votes.day")) ? new ArrayList<>(data.getStringList("votes.today-sites")) : new ArrayList<>();
        if (!sites.contains(key(service))) sites.add(key(service));
        int place = sites.indexOf(key(service)) + 1;
        marks += rules.siteBonus() * (place - 1);
        boolean allSites = menuSites.size() >= 2 && sites.containsAll(menuSites) && !day.equals(data.getString("votes.all-sites-day"));
        if (allSites) {
            marks += rules.allSitesBonus();
            data.set("votes.all-sites-day", day);
        }
        data.set("votes.today-sites", sites);
        data.set(site, now);
        data.set("votes.day", day);
        data.set("votes.paid-today", paidToday + 1);
        data.set("votes.streak-day", day);
        data.set("votes.streak", streak);
        data.set("votes.total", total);
        return new Result(Status.PAID, marks, streak, total, rules.boosterEvery() > 0 && total % rules.boosterEvery() == 0, place, allSites);
    }

    /** The streak still alive now: one that last voted today or yesterday. */
    static int streak(ConfigurationSection data, long now, ZoneId zone) {
        String streakDay = data.getString("votes.streak-day");
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        return today.toString().equals(streakDay) || today.minusDays(1).toString().equals(streakDay) ? data.getInt("votes.streak") : 0;
    }

    /** The different sites voted on today, by key. */
    static List<String> sitesToday(ConfigurationSection data, long now, ZoneId zone) {
        String day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString();
        return day.equals(data.getString("votes.day")) ? data.getStringList("votes.today-sites") : List.of();
    }

    /** When the player can next vote on a site, or 0 if they can now. Sites allow a vote a day. */
    static long nextVote(ConfigurationSection data, String service, long now) {
        long last = data.getLong("votes.sites." + key(service), 0L);
        long next = last + Duration.ofDays(1).toMillis();
        return last > 0 && next > now ? next : 0L;
    }
}
