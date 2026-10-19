# Player locations

The Staff Console shows where players connect from: country, region and city. Staff can see the geographic spread of the player base and where each player last connected from. It is built in-house and needs no third-party plugin.

## What staff see

- **Locations view** (left navigation):
  - Number of countries, located players and the top country's share.
  - A bar chart of the 12 biggest countries.
  - A table of every country with its player count, online count, share, and top five regions and cities.
  - *Everyone* counts each account once, at its last-known location. *Online now* counts only players who are online.
- **Player profile:**
  - The Profile tab shows *Connects from*, the player's last-known location.
  - The Connection tab adds a location to each of the last 10 addresses.
- Private, loopback and carrier-NAT addresses (LAN players, local test servers) show as **Local network**.

Locations are coarse: country, first-level region and city only. The database also contains latitude and longitude, but those are discarded at lookup and never stored or shown. City results for home connections are often tens of kilometres off. Mobile and VPN users show where their provider routes them.

## How it works

1. On join, `ConnectionLog` records the address as before, then hands it to `GeoIpService`.
2. `GeoIpService` looks the address up on its own background thread (`nerdsmp-geoip`), never the main thread. The lookup reads a local `.mmdb` database file, so no address leaves the server.
3. The result returns to the main thread and is stored with that address in `plugins/NerdSMP/security/connections.yml`, under `addresses.<n>.location`.
4. The Staff Console's 5-second snapshot aggregates profiles by country (`GeoSpread`). Each account counts once, at the location of its most recently used address that has one.
5. When a database first loads (a fresh install, or a new monthly edition), stored addresses without a location are resolved in one background pass. Existing history fills in without waiting for players to rejoin.

The `.mmdb` reader (`anticheat/geo/MmdbReader`) is our own. It implements the MaxMind DB format spec, memory-maps the file so a 130 MB database costs no heap, and refuses malformed files. The format is shared by DB-IP and MaxMind, so a GeoLite2 City file works too.

## The database

The default source is **DB-IP Lite City** (free, CC BY 4.0, no account or key).

- With `auto-download: true` (the default), the plugin checks once a day and downloads a new edition once a month. It saves the file as `plugins/NerdSMP/geoip/dbip-city-lite-YYYY-MM.mmdb`, about 130 MB unpacked. Early in a month, before the new edition is published, it falls back to the previous month's.
- A download is written to a temporary file and only replaces the database once it opens as a valid one. Older downloaded editions are deleted. On Windows, a file still memory-mapped is deleted on the next start instead.
- With `auto-download: false`, put any `.mmdb` file in `plugins/NerdSMP/geoip/` and restart. The newest file is used. A dropped-in `GeoLite2-City.mmdb` is never deleted.
- The **Location data** panel in the Locations view shows the database loaded, its edition, when it last updated and any download error.

**Attribution is required.** DB-IP's licence requires the credit *IP Geolocation by DB-IP* with a link to db-ip.com. The Staff Console shows it under the Locations view and on the Connection tab. If you switch to GeoLite2, the credit changes to MaxMind's automatically.

## Configuration

In `config.yml`:

```yaml
anti-cheat:
  record-ip-addresses: true
  geolocation:
    enabled: true
    auto-download: true
    download-url: https://download.db-ip.com/free/dbip-city-lite-{month}.mmdb.gz
```

`{month}` is replaced with `YYYY-MM`. A URL ending in `.gz` is unpacked while downloading.

## Privacy

- **`record-ip-addresses: false` turns locations off too.** Locations are stored on address records, so no lookups run, and stored locations are dropped with the addresses on the next save.
- **`geolocation.enabled: false`** stops lookups and downloads. Addresses are still kept, but stored locations are dropped on the next save.
- No address is ever sent to a web service. The only outbound request is the monthly database download, which contains no player data.
- Locations are personal data, like the addresses they come from. `connections.yml` stays staff-only, and the Staff Console requires a login.

## Tests

- `MmdbReaderTest` builds small `.mmdb` files with a test writer (`MmdbFixture`). It covers 24-, 28- and 32-bit records, IPv4 in IPv6 trees, IPv4-only trees, short and long pointers, extended sizes and malformed files.
- `GeoLocationTest` covers record parsing, labels, local addresses, literal-only address parsing (no DNS) and edition naming.
- `GeoSpreadTest` covers country aggregation.
- `ConnectionLogTest` covers last-known location selection.

## Limits

- Not verified against a live DB-IP download in this environment. The format handling is verified only by the spec-built fixtures.
- There is no map. The Staff Console's security policy blocks external tiles, and bundling country shapes would add hundreds of kilobytes to the page, so a bar chart and table are used instead.
- Accounts with no recorded address (they joined only while recording was off) have no location until their next join.
