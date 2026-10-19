package net.nerdsmp.paper.anticheat.geo;

import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GeoLocationTest {
    @Test void readsTheSharedCityLayoutAndPrefersEnglishNames() {
        var location = GeoLocation.fromRecord(MmdbReaderTest.dbipCity("us", "United States", "Texas", "Austin"));
        assertEquals(new GeoLocation("US", "United States", "Texas", "Austin"), location);
        assertEquals("Austin, Texas, United States", location.label());
    }
    @Test void missingPartsFallBackOrStayEmpty() {
        var countryOnly = GeoLocation.fromRecord(Map.of("country", Map.of("iso_code", "JP", "names", Map.of("ja", "日本"))));
        assertEquals(new GeoLocation("JP", "日本", "", ""), countryOnly, "any language when English is missing");
        var registered = GeoLocation.fromRecord(Map.of("registered_country", Map.of("iso_code", "NL", "names", Map.of("en", "Netherlands"))));
        assertEquals("Netherlands", registered.label());
        assertNull(GeoLocation.fromRecord(Map.of("continent", Map.of("code", "EU"))));
        assertNull(GeoLocation.fromRecord(null));
        assertEquals("Unknown", new GeoLocation(null, null, null, null).label());
        assertEquals("BR", new GeoLocation("BR", "", "", "").label());
    }
    @Test void labelsSkipRepeatedNamesAndStripControlCharacters() {
        assertEquals("Singapore", new GeoLocation("SG", "Singapore", "Singapore", "Singapore").label());
        assertEquals("Berlin, Germany", new GeoLocation("DE", "Germany", "Berlin", "Berlin").label());
        assertEquals("Evil City, Germany", new GeoLocation("DE", "Germany", "", "Evil\u0000 City\n").label());
    }
    @Test void privateAndLoopbackAddressesAreLocal() throws Exception {
        for (String local : List.of("127.0.0.1", "10.1.2.3", "192.168.0.5", "172.16.4.4", "100.64.1.1", "::1", "fe80::1", "fd12:3456::1")) {
            assertTrue(GeoLocation.isLocal(InetAddress.getByName(local)), local);
        }
        for (String remote : List.of("8.8.8.8", "100.128.0.1", "2001:4860::8888")) assertFalse(GeoLocation.isLocal(InetAddress.getByName(remote)), remote);
    }
    @Test void onlyAddressLiteralsAreParsedSoNoNameIsEverResolved() {
        assertArrayEquals(new byte[] {1, 2, 3, 4}, GeoIpService.parse("1.2.3.4").getAddress());
        assertEquals(16, GeoIpService.parse("[2001:db8::1]").getAddress().length);
        assertNotNull(GeoIpService.parse("fe80::1%eth0"));
        for (String invalid : List.of("example.com", "999.1.1.1", "1.2.3", "", " ", "localhost", "1.2.3.4.5", "gg::zz")) {
            assertNull(GeoIpService.parse(invalid), invalid);
        }
        assertNull(GeoIpService.parse(null));
    }
    @Test void monthlyEditionsAreNamedAndFetchedNewestFirst() {
        assertEquals(YearMonth.of(2026, 10), GeoIpService.monthOf("dbip-city-lite-2026-10.mmdb"));
        assertNull(GeoIpService.monthOf("GeoLite2-City.mmdb"));
        assertNull(GeoIpService.monthOf("dbip-city-lite-2026-13.mmdb"));
        var now = YearMonth.of(2026, 10);
        assertEquals(List.of(now, YearMonth.of(2026, 9)), GeoIpService.downloadCandidates(now, null));
        assertEquals(List.of(now), GeoIpService.downloadCandidates(now, YearMonth.of(2026, 9)));
        assertEquals(List.of(), GeoIpService.downloadCandidates(now, now));
        assertEquals("https://download.db-ip.com/free/dbip-city-lite-2026-10.mmdb.gz", GeoIpService.url(GeoIpService.DEFAULT_URL, now));
    }
    @Test void creditFollowsTheDatabaseInUse() {
        assertEquals("IP Geolocation by DB-IP", GeoIpService.attribution("DBIP-City-Lite"));
        assertEquals("https://db-ip.com", GeoIpService.attributionUrl("DBIP-City-Lite"));
        assertTrue(GeoIpService.attribution("GeoLite2-City").contains("MaxMind"));
    }
}
