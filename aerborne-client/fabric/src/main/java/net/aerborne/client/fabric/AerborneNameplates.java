package net.aerborne.client.fabric;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Nameplates over the creatures the server lists (PlatesPayload). They are drawn in the world as the creature's own
 * name tag (see EntityRendererMixin): the server's name on top and a health bar from the entity's health beneath,
 * so they sit at the head, hide behind the camera, and move with the creature like any name tag.
 */
public final class AerborneNameplates {
    private AerborneNameplates() { }

    private static final int SEGMENTS = 10;
    private static final int PLATE_EFFECTS = 4;
    private static final double RANGE_SQUARED = 32 * 32;
    private static PlatesPayload indexed;
    private static Map<Integer, PlatesPayload.Plate> byId = Map.of();

    /** The name and health lines for this entity, or null when it has no plate. */
    public static Component[] lines(Entity entity, double distanceSquared) {
        PlatesPayload plates = ClientState.plates;
        if (plates == null || !ClientState.preferences.target || distanceSquared > RANGE_SQUARED) return null;
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isInvisible()) return null;
        PlatesPayload.Plate plate = plate(entity.getId());
        if (plate == null || plate.npc()) return null;
        float max = Math.max(1, living.getMaxHealth()), health = Math.max(0, Math.min(max, living.getHealth()));
        float progress = health / max;
        int filled = health <= 0 ? 0 : Math.max(1, Math.round(progress * SEGMENTS));
        // One quiet scheme for every creature: a red bar, and the name coloured only by rank.
        MutableComponent bar = Component.empty()
                .append(Component.literal("■".repeat(filled)).withColor(0xD9534F))
                .append(Component.literal("■".repeat(SEGMENTS - filled)).withColor(0x3A4152))
                .append(Component.literal(" " + AerborneHud.number(health)).withColor(0xC8CDD8));
        // Its status effects follow the health, debuffs first, so a fight's state reads at a glance.
        if (ClientState.preferences.plateEffects) {
            Component effects = AerborneEffects.plateLine(AerborneEffects.on(living), PLATE_EFFECTS);
            if (effects != null) bar.append(Component.literal("  ")).append(effects);
        }
        return new Component[] {name(plate), bar};
    }

    /** The server's plate for this entity id, or null. */
    static PlatesPayload.Plate plate(int entityId) {
        PlatesPayload plates = ClientState.plates;
        if (plates == null) return null;
        if (plates != indexed) {
            Map<Integer, PlatesPayload.Plate> map = new HashMap<>();
            for (PlatesPayload.Plate plate : plates.plates()) map.put(plate.entityId(), plate);
            byId = map; indexed = plates;
        }
        return byId.get(entityId);
    }

    /** The plate's name coloured by rank, with a leading "[Lv 2]" tag kept grey. */
    static MutableComponent name(PlatesPayload.Plate plate) {
        String plain = plate.name().replaceAll("§[0-9a-fk-orx]", "").replaceAll("§#?[0-9a-fA-F]{6}", "").strip();
        int nameColour = plate.boss() ? 0xC9A2FF : plate.elite() ? 0xF4C76B : plate.npc() ? 0x8FD3FF : 0xF2F2F2;
        MutableComponent name = Component.empty();
        // A leading "[Lv 2]" tag stays grey so the name itself reads first.
        java.util.regex.Matcher tag = java.util.regex.Pattern.compile("^(\\[[^\\]]*\\])\\s*").matcher(plain);
        if (tag.find()) {
            name.append(Component.literal(tag.group(1) + " ").withColor(0x9AA3B5));
            plain = plain.substring(tag.end());
        }
        name.append(Component.literal(plain).withColor(nameColour));
        return name;
    }
}
