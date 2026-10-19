package net.nerdsmp.paper.anticheat.geo;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GeoSpreadTest {
    private static GeoSpread.Player at(String code, String country, String region, String city, boolean online) {
        return new GeoSpread.Player(new GeoLocation(code, country, region, city), online);
    }

    @Test void countsEachPlayerOnceAndSortsByPlayers() {
        var summary = GeoSpread.of(List.of(
            at("GB", "United Kingdom", "England", "London", true),
            at("US", "United States", "Texas", "Austin", false),
            at("GB", "United Kingdom", "Scotland", "Glasgow", false),
            at("GB", "United Kingdom", "England", "Leeds", true),
            new GeoSpread.Player(null, true)));
        assertEquals(4, summary.located());
        assertEquals(1, summary.unlocated());
        assertEquals(2, summary.onlineLocated());
        var uk = summary.countries().getFirst();
        assertEquals("United Kingdom", uk.name());
        assertEquals(3, uk.players());
        assertEquals(2, uk.online());
        assertEquals(List.of(new GeoSpread.Place("England", 2), new GeoSpread.Place("Scotland", 1)), uk.regions());
        assertEquals(List.of("Glasgow, Scotland", "Leeds, England", "London, England"), uk.cities().stream().map(GeoSpread.Place::name).toList());
        assertEquals("United States", summary.countries().get(1).name());
    }
    @Test void tiesSortByNameAndPlaceListsAreCapped() {
        List<GeoSpread.Player> players = new ArrayList<>();
        for (int i = 0; i < 8; i++) players.add(at("DE", "Germany", "Region " + i, "City " + i, false));
        players.add(at("AT", "Austria", "", "", false));
        players.add(at("AT", "Austria", "Vienna", "Vienna", false));
        var summary = GeoSpread.of(players);
        assertEquals(List.of("Germany", "Austria"), summary.countries().stream().map(GeoSpread.Country::name).toList());
        assertEquals(GeoSpread.TOP_PLACES, summary.countries().getFirst().regions().size());
        assertEquals(List.of(new GeoSpread.Place("Vienna", 1)), summary.countries().get(1).cities(), "city equal to its region is not repeated");

        var tie = GeoSpread.of(List.of(at("ZA", "South Africa", "", "", false), at("AR", "Argentina", "", "", false)));
        assertEquals(List.of("Argentina", "South Africa"), tie.countries().stream().map(GeoSpread.Country::name).toList());
    }
    @Test void localNetworkAndUnknownLocationsAreKeptApart() {
        var summary = GeoSpread.of(List.of(new GeoSpread.Player(GeoLocation.LOCAL, true),
            new GeoSpread.Player(new GeoLocation("", "", "", ""), false), new GeoSpread.Player(null, false)));
        assertEquals(1, summary.located());
        assertEquals(2, summary.unlocated());
        assertEquals("Local network", summary.countries().getFirst().name());
    }
    @Test void noPlayersMeansNoCountries() {
        var summary = GeoSpread.of(List.of());
        assertEquals(0, summary.located());
        assertTrue(summary.countries().isEmpty());
    }
}
