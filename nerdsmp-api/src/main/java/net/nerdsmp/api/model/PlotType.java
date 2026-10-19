package net.nerdsmp.api.model;

import java.util.Map;

/** Player-facing land-use presets; no economy, ownership, or protection bypasses. */
public enum PlotType {
    COMMONS, RESIDENTIAL, FARM, WORKSHOP, PRESERVE;

    public boolean privateAccess() { return this == RESIDENTIAL; }

    public Map<ClaimFlag, Boolean> preset() {
        if (this == COMMONS) return Map.of();
        return Map.of(
            ClaimFlag.EXPLOSIONS, false,
            ClaimFlag.FIRE_SPREAD, false,
            ClaimFlag.MOB_GRIEFING, false,
            ClaimFlag.PUBLIC_INTERACT, this == FARM || this == WORKSHOP,
            ClaimFlag.PUBLIC_CONTAINERS, false,
            ClaimFlag.PUBLIC_HARVEST, this == FARM,
            ClaimFlag.PUBLIC_ANIMALS, this == FARM,
            ClaimFlag.HOSTILE_SPAWNING, this != PRESERVE,
            ClaimFlag.PVP, false);
    }
}
