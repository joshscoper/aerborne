package net.nerdsmp.paper.anticheat.geo;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MmdbReaderTest {
    static Map<String, Object> dbipCity(String code, String country, String region, String city) {
        return Map.of(
            "city", Map.of("names", Map.of("en", city)),
            "continent", Map.of("code", "EU", "names", Map.of("en", "Europe")),
            "country", Map.of("iso_code", code, "names", Map.of("en", country, "de", country + "-de")),
            "location", Map.of("latitude", 51.5, "longitude", -0.12),
            "subdivisions", List.of(Map.of("names", Map.of("en", region))));
    }
    private static MmdbReader reader(byte[] bytes) throws IOException { return new MmdbReader(ByteBuffer.wrap(bytes)); }

    @Test void ipv4AddressesResolveThroughAnIpv6TreeAtEveryRecordSize() throws Exception {
        for (int size : new int[] {24, 28, 32}) {
            var db = reader(new MmdbFixture(6)
                .insert("81.2.69.0", 24, dbipCity("GB", "United Kingdom", "England", "London"))
                .insert("2001:db8::", 32, dbipCity("DE", "Germany", "Berlin", "Berlin")).build(size));
            var london = GeoLocation.fromRecord(db.lookup(InetAddress.getByName("81.2.69.160")));
            assertEquals(new GeoLocation("GB", "United Kingdom", "England", "London"), london, "record size " + size);
            assertEquals("Berlin, Germany", GeoLocation.fromRecord(db.lookup(InetAddress.getByName("2001:db8::7"))).label());
            assertNull(db.lookup(InetAddress.getByName("81.2.70.1")), "neighbouring network has no data");
            assertNull(db.lookup(InetAddress.getByName("2001:db9::1")));
        }
    }
    @Test void metadataIsReadAndCoordinatesNeverReachTheLocation() throws Exception {
        var db = reader(new MmdbFixture(6).insert("81.2.69.0", 24, dbipCity("GB", "United Kingdom", "England", "London")).build(24));
        assertEquals("DBIP-City-Lite", db.databaseType());
        assertEquals(1_790_000_000L, db.buildEpoch());
        var record = db.lookup(InetAddress.getByName("81.2.69.1"));
        assertEquals(51.5, ((Map<?, ?>) record.get("location")).get("latitude"));
        assertFalse(GeoLocation.fromRecord(record).toString().contains("51.5"));
    }
    @Test void ipv4OnlyDatabasesAcceptIpv4MappedIpv6Addresses() throws Exception {
        var db = reader(new MmdbFixture(4).insert("10.20.0.0", 16, dbipCity("FR", "France", "Île-de-France", "Paris")).build(24));
        assertEquals("FR", GeoLocation.fromRecord(db.lookup(InetAddress.getByAddress(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, 10, 20, 3, 4}))).countryCode());
        assertEquals("Paris, Île-de-France, France", GeoLocation.fromRecord(db.lookup(InetAddress.getByName("10.20.255.255"))).label());
        assertNull(db.lookup(InetAddress.getByName("2001:db8::1")), "native IPv6 has no place in an IPv4 tree");
    }
    @Test void pointersAreFollowedAtBothShortAndLongDistances() throws Exception {
        var fixture = new MmdbFixture(6);
        int near = fixture.raw("Toronto");
        fixture.raw("x".repeat(2_100)); // pushes the next value past the 2048-byte short-pointer range
        int far = fixture.raw("Canada");
        fixture.insert("24.0.0.0", 8, Map.of(
            "city", Map.of("names", Map.of("en", new MmdbFixture.Pointer(near))),
            "country", Map.of("iso_code", "CA", "names", Map.of("en", new MmdbFixture.Pointer(far))),
            "subdivisions", List.of(Map.of("names", Map.of("en", "Ontario")))));
        var db = reader(fixture.build(24));
        assertEquals("Toronto, Ontario, Canada", GeoLocation.fromRecord(db.lookup(InetAddress.getByName("24.1.2.3"))).label());
    }
    @Test void longStringsUseExtendedSizes() throws Exception {
        String longName = "Llanfair".repeat(40); // 320 bytes: the two-byte size form
        var db = reader(new MmdbFixture(6).insert("1.0.0.0", 8, Map.of("country", Map.of("iso_code", "GB", "names", Map.of("en", longName)), "flag", true)).build(24));
        var record = db.lookup(InetAddress.getByName("1.1.1.1"));
        assertEquals(longName, ((Map<?, ?>) ((Map<?, ?>) record.get("country")).get("names")).get("en"));
        assertEquals(true, record.get("flag"));
        assertEquals(64, GeoLocation.fromRecord(record).country().length(), "stored names are capped");
    }
    @Test void malformedFilesAreRefusedWithAnIoException() {
        assertThrows(IOException.class, () -> reader(new byte[256]));
        assertThrows(IOException.class, () -> reader(new MmdbFixture(6).metadata(Map.of("node_count", 1_000_000)).build(24)));
        assertThrows(IOException.class, () -> reader(new MmdbFixture(6).metadata(Map.of("record_size", 20)).build(24)));
        assertThrows(IOException.class, () -> reader(new MmdbFixture(6).metadata(Map.of("ip_version", 5)).build(24)));
    }
}
