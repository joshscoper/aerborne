package net.aerborne.client.fabric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Status effects on creatures, for the nameplates and the target frame. The local player's own come from the game;
 * other creatures' come from the server (EffectsPayload), counted down here between its updates.
 */
public final class AerborneEffects {
    private AerborneEffects() { }

    /** One effect as shown: its sprite, level, whether it hurts, and when it ends ({@code -1} never). */
    public record Shown(Identifier key, MobEffect effect, int amplifier, long endsAtMillis, long fullMillis) {
        public Shown(Identifier key, MobEffect effect, int amplifier, long endsAtMillis) {
            this(key, effect, amplifier, endsAtMillis, endsAtMillis < 0 ? -1 : Math.max(1, endsAtMillis - System.currentTimeMillis()));
        }
        /** How much of the effect is left, 1 for one with no end. */
        public float fraction(long now) {
            if (endless() || fullMillis <= 0) return 1;
            return Math.clamp((endsAtMillis - now) / (float) fullMillis, 0F, 1F);
        }
        public boolean harmful() { return effect != null && effect.getCategory() == MobEffectCategory.HARMFUL; }
        public boolean endless() { return endsAtMillis < 0; }
        public long secondsLeft(long now) { return endless() ? -1 : Math.max(0, (endsAtMillis - now + 999) / 1000); }
        public Identifier sprite() { return key.withPrefix("mob_effect/"); }
        public String name() { return effect == null ? key.getPath() : effect.getDisplayName().getString(); }
    }

    /** Debuffs first (what a fighter acts on), then the rest, each with the soonest to end first. */
    private static final Comparator<Shown> ORDER = Comparator.comparing((Shown s) -> !s.harmful())
            .thenComparingLong(s -> s.endless() ? Long.MAX_VALUE : s.endsAtMillis);

    private static volatile Map<Integer, List<Shown>> byEntity = Map.of();
    /** The longest time left each effect has had since it began ("entity/key"), so its bar drains from full. */
    private static final Map<String, Long> longest = new HashMap<>();
    private static volatile long receivedAt;

    static void accept(EffectsPayload payload) {
        long now = System.currentTimeMillis();
        Map<Integer, List<Shown>> map = new HashMap<>();
        for (EffectsPayload.Entry entry : payload.entries()) {
            List<Shown> list = new ArrayList<>(entry.effects().size());
            for (EffectsPayload.Effect e : entry.effects()) {
                Identifier key = Identifier.tryParse(e.key());
                if (key == null) continue;
                MobEffect effect = BuiltInRegistries.MOB_EFFECT.getValue(key);
                long left = e.ticks() < 0 ? -1 : e.ticks() * 50L;
                long full = left < 0 ? -1 : longest.merge(entry.entityId() + "/" + key, left, Math::max);
                list.add(new Shown(key, effect, e.amplifier(), left < 0 ? -1 : now + left, full));
            }
            list.sort(ORDER);
            if (!list.isEmpty()) map.put(entry.entityId(), List.copyOf(list));
        }
        byEntity = map;
        receivedAt = now;
        int self = Minecraft.getInstance().player == null ? Integer.MIN_VALUE : Minecraft.getInstance().player.getId();
        longest.keySet().removeIf(id -> {
            int slash = id.indexOf('/');
            int entity = Integer.parseInt(id.substring(0, slash));
            if (entity == self) return false;
            List<Shown> on = map.get(entity);
            return on == null || on.stream().noneMatch(s -> s.key.toString().equals(id.substring(slash + 1)));
        });
    }

    static void clear() { byEntity = Map.of(); longest.clear(); }

    /** The effects on this entity, debuffs first; empty when it has none or the server sent none. */
    public static List<Shown> on(Entity entity) {
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player && entity instanceof LivingEntity self) {
            List<Shown> own = new ArrayList<>();
            for (MobEffectInstance i : self.getActiveEffects()) {
                if (!i.showIcon()) continue;
                var key = i.getEffect().unwrapKey();
                if (key.isEmpty()) continue;
                long left = i.isInfiniteDuration() ? -1 : i.getDuration() * 50L;
                String id = entity.getId() + "/" + key.get().identifier();
                long full = left < 0 ? -1 : longest.merge(id, left, Math::max);
                own.add(new Shown(key.get().identifier(), i.getEffect().value(), i.getAmplifier(), left < 0 ? -1 : now + left, full));
            }
            String prefix = entity.getId() + "/";
            longest.keySet().removeIf(id -> id.startsWith(prefix) && own.stream().noneMatch(s -> id.equals(prefix + s.key())));
            own.sort(ORDER);
            return own;
        }
        List<Shown> sent = byEntity.get(entity.getId());
        if (sent == null) return List.of();
        // Drop what has run out since the last update; the server's next update confirms it.
        return sent.stream().filter(s -> s.endless() || s.endsAtMillis > now).toList();
    }

    /** The effect's icon as a text glyph, for name tags. */
    public static MutableComponent glyph(Shown shown) {
        return Component.object(new AtlasSprite(AtlasIds.GUI, shown.sprite()));
    }

    /**
     * A compact effects line for a nameplate: up to {@code limit} icons, each with its level when above I and its
     * seconds when nearly over, then "+n" for the rest. Null when there are none.
     */
    public static Component plateLine(List<Shown> effects, int limit) {
        if (effects.isEmpty()) return null;
        long now = System.currentTimeMillis();
        MutableComponent line = Component.empty();
        int shown = Math.min(limit, effects.size());
        for (int i = 0; i < shown; i++) {
            Shown s = effects.get(i);
            if (i > 0) line.append(Component.literal(" "));
            line.append(glyph(s));
            if (s.amplifier() > 0) line.append(Component.literal(roman(s.amplifier() + 1)).withColor(s.harmful() ? 0xFF8A80 : 0xF4C76B));
            long seconds = s.secondsLeft(now);
            if (seconds >= 0 && seconds <= 10) line.append(Component.literal(seconds + "s").withColor(0xF6C85F));
        }
        if (effects.size() > shown) line.append(Component.literal(" +" + (effects.size() - shown)).withColor(0x9AA3B5));
        return line;
    }

    public static String roman(int value) {
        return switch (value) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> Integer.toString(value);
        };
    }

    /** "12s", "3m", "1h", or "" for no end: short enough to sit under an icon. */
    public static String shortTime(long seconds) {
        if (seconds < 0) return "";
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h";
    }
}
