package net.nerdsmp.paper.webapi;

import org.bukkit.configuration.ConfigurationSection;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Strict, versioned configuration. A typo never silently opens another interface or origin. */
public record ApiSettings(boolean enabled, InetAddress bind, int port, Set<String> allowedHosts,
                          Set<String> corsOrigins, int snapshotSeconds, int globalPerMinute,
                          int perAddressPerMinute) {
    public ApiSettings {
        if (bind == null || bind.isMulticastAddress() || port < 0 || port > 65_535
            || port == 0 && !bind.isLoopbackAddress() || port > 0 && port < 1_024
            || snapshotSeconds < 2 || snapshotSeconds > 60
            || globalPerMinute < 10 || globalPerMinute > 10_000
            || perAddressPerMinute < 5 || perAddressPerMinute > globalPerMinute) {
            throw new IllegalArgumentException("Invalid web API limits");
        }
        allowedHosts = hosts(List.copyOf(allowedHosts));
        corsOrigins = origins(List.copyOf(corsOrigins));
    }

    public static ApiSettings read(ConfigurationSection yaml) throws IOException {
        Set<String> keys = Set.of("schema", "enabled", "bind", "port", "allowed-hosts", "cors-origins",
            "snapshot-seconds", "global-per-minute", "per-address-per-minute");
        if (!yaml.getKeys(false).equals(keys) || !yaml.isInt("schema") || yaml.getInt("schema") != 1
            || !yaml.isBoolean("enabled") || !yaml.isString("bind") || !yaml.isInt("port")
            || !yaml.isInt("snapshot-seconds")
            || !yaml.isInt("global-per-minute") || !yaml.isInt("per-address-per-minute")) {
            throw new IllegalArgumentException("Invalid security/web-api.yml; expected schema 1 and supported keys");
        }
        return new ApiSettings(yaml.getBoolean("enabled"), address(yaml.getString("bind")), yaml.getInt("port"),
            hosts(strings(yaml, "allowed-hosts")), origins(strings(yaml, "cors-origins")),
            yaml.getInt("snapshot-seconds"), yaml.getInt("global-per-minute"), yaml.getInt("per-address-per-minute"));
    }

    private static List<String> strings(ConfigurationSection yaml, String key) {
        if (!yaml.isList(key) || yaml.getList(key).stream().anyMatch(value -> !(value instanceof String))) {
            throw new IllegalArgumentException("Web API " + key + " must be a list of strings");
        }
        return yaml.getStringList(key);
    }

    /** Only literal IPv4 addresses: configuration cannot initiate DNS lookups. */
    static InetAddress address(String value) throws IOException {
        if (value == null || !value.matches("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}")) {
            throw new IllegalArgumentException("Web API bind must be a literal IPv4 address");
        }
        String[] parts = value.split("\\.");
        byte[] bytes = new byte[4];
        for (int index = 0; index < bytes.length; index++) {
            int part = Integer.parseInt(parts[index]);
            if (part > 255) throw new IllegalArgumentException("Invalid web API IPv4 address");
            bytes[index] = (byte) part;
        }
        InetAddress address = InetAddress.getByAddress(bytes);
        if (address.isMulticastAddress() || value.equals("255.255.255.255")) {
            throw new IllegalArgumentException("Web API cannot bind multicast or broadcast addresses");
        }
        return address;
    }

    static Set<String> hosts(List<String> values) {
        if (values.size() > 16) throw new IllegalArgumentException("At most 16 API hosts may be configured");
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (value == null || value.length() > 259
                || !value.matches("(?i)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?::[0-9]{1,5})?")) {
                throw new IllegalArgumentException("API allowed-hosts needs exact hostnames with optional ports, not URLs");
            }
            int colon = value.indexOf(':');
            if (colon >= 0 && (Integer.parseInt(value.substring(colon + 1)) < 1
                || Integer.parseInt(value.substring(colon + 1)) > 65_535)) {
                throw new IllegalArgumentException("Invalid API host port");
            }
            result.add(value.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(result);
    }

    static Set<String> origins(List<String> values) {
        if (values.size() > 16) throw new IllegalArgumentException("At most 16 API origins may be configured");
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (value == null || value.length() > 280) throw new IllegalArgumentException("Invalid API CORS origin");
            URI uri;
            try { uri = URI.create(value); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid API CORS origin"); }
            String host = uri.getHost();
            boolean local = "localhost".equals(host) || "127.0.0.1".equals(host);
            if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !"".equals(uri.getRawPath()) || uri.getPort() == 0 || uri.getPort() > 65_535
                || !("https".equals(uri.getScheme()) || local && "http".equals(uri.getScheme()))
                || !value.equals(uri.toASCIIString()) || value.contains("*")) {
                throw new IllegalArgumentException("Use an exact HTTPS origin, or HTTP localhost for development; no path or wildcard");
            }
            // Origins are case-sensitive strings in browser requests; require their canonical lowercase authority.
            if (!value.equals(value.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Use lowercase API CORS origins");
            result.add(value);
        }
        return Set.copyOf(result);
    }
}
