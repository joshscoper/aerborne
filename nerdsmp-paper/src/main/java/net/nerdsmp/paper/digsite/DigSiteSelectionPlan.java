package net.nerdsmp.paper.digsite;

import net.nerdsmp.paper.zone.ZoneDefinition;
import net.nerdsmp.paper.zone.ZoneDefinition.Bounds;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Resolves a selected excavation cuboid without changing an existing encounter zone. */
public final class DigSiteSelectionPlan {
    public static final long MAX_SCAN_VOLUME = 32_768;

    private DigSiteSelectionPlan() { }

    public static Plan plan(List<ZoneDefinition> zones, String id, UUID world, Bounds bounds) {
        if (!DigSiteDefinition.validId(id)) {
            throw new IllegalArgumentException("Dig-site IDs use 1–48 lowercase letters, numbers, underscores or dashes.");
        }
        Objects.requireNonNull(world, "Selection world UUID is required");
        Objects.requireNonNull(bounds, "Select both corners before creating a dig site");
        List<ZoneDefinition> snapshot = List.copyOf(Objects.requireNonNull(zones, "Existing zones are required"));
        if (volume(bounds) > MAX_SCAN_VOLUME) {
            throw new IllegalArgumentException("Select a digging area of at most 32,768 blocks. Mark a smaller cuboid with the wand.");
        }
        if (snapshot.size() > ZoneDefinition.MAX_ZONES) {
            throw new IllegalArgumentException("At most 128 zones are supported. Review the zone definitions before adding a dig site.");
        }

        List<ZoneDefinition> overlaps = snapshot.stream()
            .filter(zone -> zone.worldId().equals(world) && zone.bounds().overlaps(bounds)).toList();
        if (overlaps.size() > 1) {
            throw new IllegalArgumentException("The selection crosses multiple zone boundaries. Keep both corners wholly inside one zone, or outside all zones.");
        }
        if (overlaps.size() == 1) {
            ZoneDefinition parent = overlaps.getFirst();
            if (!contains(parent.bounds(), bounds)) {
                throw new IllegalArgumentException("The selection crosses the boundary of zone " + parent.id()
                    + ". Keep the entire cuboid inside that zone, or outside it.");
            }
            if (!parent.enabled()) {
                throw new IllegalArgumentException("Zone " + parent.id() + " is disabled. Enable it before capturing a dig site there.");
            }
            return new Plan(parent, false);
        }

        if (snapshot.stream().anyMatch(zone -> zone.id().equals(id))) {
            throw new IllegalArgumentException("A zone named " + id + " already exists elsewhere. Choose a different dig-site ID; existing zones will not be replaced.");
        }
        if (snapshot.size() == ZoneDefinition.MAX_ZONES) {
            throw new IllegalArgumentException("The server already has 128 zones. Select inside an enabled zone, or remove an unused zone first.");
        }
        return new Plan(new ZoneDefinition(id, id.replace('_', ' '), world, bounds,
            true, true, 8, 20, 48, List.of(), List.of()), true);
    }

    public static long volume(Bounds bounds) {
        Objects.requireNonNull(bounds, "Selection bounds are required");
        return ((long) bounds.maxX() - bounds.minX() + 1)
            * ((long) bounds.maxY() - bounds.minY() + 1)
            * ((long) bounds.maxZ() - bounds.minZ() + 1);
    }

    private static boolean contains(Bounds outer, Bounds inner) {
        return inner.minX() >= outer.minX() && inner.maxX() <= outer.maxX()
            && inner.minY() >= outer.minY() && inner.maxY() <= outer.maxY()
            && inner.minZ() >= outer.minZ() && inner.maxZ() <= outer.maxZ();
    }

    public record Plan(ZoneDefinition zone, boolean created) {
        public Plan { Objects.requireNonNull(zone, "Planned zone is required"); }
    }
}
