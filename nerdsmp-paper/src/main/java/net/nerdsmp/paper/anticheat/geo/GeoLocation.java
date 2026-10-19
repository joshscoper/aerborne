package net.nerdsmp.paper.anticheat.geo;

import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Coarse location of a connection: country, first-level region and city. Coordinates from the database are never
 * kept. Empty strings mean the database did not say.
 */
public record GeoLocation(String countryCode, String country, String region, String city) {
    /** Country code used for private, loopback and link-local addresses (LAN players, local test servers). */
    public static final String LOCAL_CODE = "LAN";
    public static final GeoLocation LOCAL = new GeoLocation(LOCAL_CODE, "Local network", "", "");

    public GeoLocation {
        countryCode = clean(countryCode, 3).toUpperCase(Locale.ROOT);
        country = clean(country, 64);
        region = clean(region, 64);
        city = clean(city, 64);
    }

    public boolean known() {
        return !countryCode.isEmpty() || !country.isEmpty();
    }

    /** "City, Region, Country", skipping parts the database left out and repeats such as "Singapore, Singapore". */
    public String label() {
        List<String> parts = new java.util.ArrayList<>();
        for (String part : new String[] {city, region, country.isEmpty() ? countryCode : country}) {
            if (!part.isEmpty() && (parts.isEmpty() || !parts.getLast().equalsIgnoreCase(part))) parts.add(part);
        }
        return parts.isEmpty() ? "Unknown" : String.join(", ", parts);
    }

    /**
     * Reads the GeoIP2-City layout that DB-IP Lite and GeoLite2 share: {@code country.iso_code},
     * {@code country.names.en}, {@code subdivisions[0].names.en}, {@code city.names.en}. Anything else is ignored.
     */
    public static GeoLocation fromRecord(Map<String, Object> record) {
        if (record == null) return null;
        Map<?, ?> country = map(record.get("country"));
        if (country == null) country = map(record.get("registered_country"));
        String code = country == null ? "" : text(country.get("iso_code"));
        String countryName = name(country);
        String region = "";
        if (record.get("subdivisions") instanceof List<?> subdivisions && !subdivisions.isEmpty()) region = name(map(subdivisions.getFirst()));
        String city = name(map(record.get("city")));
        GeoLocation location = new GeoLocation(code, countryName, region, city);
        return location.known() ? location : null;
    }

    /** Addresses no public database can place. */
    public static boolean isLocal(InetAddress address) {
        return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
            || address.isAnyLocalAddress() || isUniqueLocal(address) || isCarrierNat(address);
    }

    private static boolean isUniqueLocal(InetAddress address) { // fc00::/7
        byte[] raw = address.getAddress();
        return raw.length == 16 && (raw[0] & 0xFE) == 0xFC;
    }

    private static boolean isCarrierNat(InetAddress address) { // 100.64.0.0/10
        byte[] raw = address.getAddress();
        return raw.length == 4 && (raw[0] & 0xFF) == 100 && (raw[1] & 0xC0) == 64;
    }

    private static String name(Map<?, ?> section) {
        if (section == null || !(section.get("names") instanceof Map<?, ?> names)) return "";
        String english = text(names.get("en"));
        if (!english.isEmpty()) return english;
        return names.values().stream().map(GeoLocation::text).filter(value -> !value.isEmpty()).findFirst().orElse("");
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }

    private static String text(Object value) {
        return value instanceof String string ? string : "";
    }

    private static String clean(String value, int maximum) {
        if (value == null) return "";
        String stripped = value.strip().codePoints().filter(point -> !Character.isISOControl(point))
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        return stripped.length() > maximum ? stripped.substring(0, maximum) : stripped;
    }
}
