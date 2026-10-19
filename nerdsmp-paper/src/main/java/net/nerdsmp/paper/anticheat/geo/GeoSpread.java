package net.nerdsmp.paper.anticheat.geo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Players by country, counting each account once at its last-known location. Pure, so it is unit-tested. */
public final class GeoSpread {
    static final int TOP_PLACES = 5;

    private GeoSpread() {}

    /** One account: its last-known location (null when never resolved) and whether it is online now. */
    public record Player(GeoLocation location, boolean online) {}

    public record Place(String name, int players) {}

    public record Country(String code, String name, int players, int online, List<Place> regions, List<Place> cities) {}

    public record Summary(int located, int unlocated, int onlineLocated, List<Country> countries) {}

    public static Summary of(Collection<Player> players) {
        Map<String, Tally> byCountry = new HashMap<>();
        int unlocated = 0;
        int onlineLocated = 0;
        for (Player player : players) {
            GeoLocation location = player.location();
            if (location == null || !location.known()) { unlocated++; continue; }
            String key = location.countryCode().isEmpty() ? location.country() : location.countryCode();
            Tally tally = byCountry.computeIfAbsent(key, ignored -> new Tally(location.countryCode(), location.country()));
            if (tally.name.isEmpty()) tally.name = location.country();
            tally.players++;
            if (player.online()) { tally.online++; onlineLocated++; }
            if (!location.region().isEmpty()) tally.regions.merge(location.region(), 1, Integer::sum);
            if (!location.city().isEmpty()) {
                String city = location.region().isEmpty() || location.region().equalsIgnoreCase(location.city())
                    ? location.city() : location.city() + ", " + location.region();
                tally.cities.merge(city, 1, Integer::sum);
            }
        }
        List<Country> countries = byCountry.values().stream()
            .map(tally -> new Country(tally.code, tally.name.isEmpty() ? tally.code : tally.name, tally.players, tally.online, top(tally.regions), top(tally.cities)))
            .sorted(Comparator.comparingInt(Country::players).reversed().thenComparing(Country::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
        return new Summary(players.size() - unlocated, unlocated, onlineLocated, countries);
    }

    private static List<Place> top(Map<String, Integer> counts) {
        List<Place> places = new ArrayList<>();
        counts.forEach((name, count) -> places.add(new Place(name, count)));
        places.sort(Comparator.comparingInt(Place::players).reversed().thenComparing(Place::name, String.CASE_INSENSITIVE_ORDER));
        return places.size() > TOP_PLACES ? List.copyOf(places.subList(0, TOP_PLACES)) : List.copyOf(places);
    }

    private static final class Tally {
        private final String code;
        private String name;
        private int players;
        private int online;
        private final Map<String, Integer> regions = new HashMap<>();
        private final Map<String, Integer> cities = new HashMap<>();

        private Tally(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }
}
