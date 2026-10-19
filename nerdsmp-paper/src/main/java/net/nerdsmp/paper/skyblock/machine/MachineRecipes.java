package net.nerdsmp.paper.skyblock.machine;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Pure nine-slot transformations: inputs 0-2, fuel 3-5, outputs 6-8. */
public final class MachineRecipes {
    private static final List<Recipe> RECIPES = recipes();
    private MachineRecipes() { }

    public record Recipe(MachineKind kind, int stage, Material input, int amount,
                         int fuel, Material output, int result) {
        public Recipe {
            if (kind == null || input == null || output == null || stage < kind.stage() || stage > 5
                || amount < 1 || amount > 64 || fuel < 1 || fuel > 64 || result < 1 || result > 64)
                throw new IllegalArgumentException("Invalid machine recipe");
        }
        public String description() {
            return amount + " " + title(input) + " + " + fuel + " coal = " + result + " " + title(output)
                + (stage > kind.stage() ? " (" + net.nerdsmp.paper.skyblock.IslandResourceStage.at(stage).title() + ")" : "");
        }
    }
    /** Metadata-bearing stacks are deliberately ineligible for consumption or output merging. */
    public record Stack(Material material, int amount, boolean plain) {
        public Stack {
            // Every recipe ingredient/result is a standard 64-stack material. Do not query
            // Paper's live item registry here: the planner must also run without a server.
            if (material == null || material == Material.AIR || material == Material.CAVE_AIR
                || material == Material.VOID_AIR || amount < 1 || amount > 64)
                throw new IllegalArgumentException("Invalid machine stack");
        }
    }
    public record Plan(Recipe recipe, List<Stack> slots) {
        public Plan { slots = java.util.Collections.unmodifiableList(new ArrayList<>(slots)); }
    }
    public static List<Recipe> forKind(MachineKind kind) { return RECIPES.stream().filter(r -> r.kind() == kind).toList(); }
    public static boolean acceptsInput(MachineKind kind, Material material) {
        return forKind(kind).stream().anyMatch(r -> r.input() == material);
    }
    public static boolean fuel(Material material) { return material == Material.COAL || material == Material.CHARCOAL; }
    public static Optional<Plan> plan(MachineKind kind, int stage, List<Stack> slots) {
        if (slots.size() != 9 || stage < 0 || stage > 5) throw new IllegalArgumentException("Invalid machine inventory or tier");
        for (Recipe recipe : forKind(kind)) {
            if (stage < recipe.stage() || total(slots, 0, 3, recipe.input(), false) < recipe.amount()
                || total(slots, 3, 6, null, true) < recipe.fuel()) continue;
            ArrayList<Stack> next = new ArrayList<>(slots);
            if (!insert(next, recipe.output(), recipe.result())) continue;
            consume(next, 0, 3, recipe.input(), false, recipe.amount());
            consume(next, 3, 6, null, true, recipe.fuel());
            return Optional.of(new Plan(recipe, next));
        }
        return Optional.empty();
    }
    private static int total(List<Stack> slots, int from, int to, Material material, boolean fuel) {
        int count = 0;
        for (int slot = from; slot < to; slot++) {
            Stack stack = slots.get(slot);
            if (eligible(stack, material, fuel)) count += stack.amount();
        }
        return count;
    }
    private static boolean eligible(Stack stack, Material material, boolean isFuel) {
        return stack != null && stack.plain() && (isFuel ? fuel(stack.material()) : stack.material() == material);
    }
    private static void consume(List<Stack> slots, int from, int to, Material material, boolean fuel, int amount) {
        for (int slot = from; slot < to && amount > 0; slot++) {
            Stack stack = slots.get(slot);
            if (!eligible(stack, material, fuel)) continue;
            int take = Math.min(amount, stack.amount()); amount -= take;
            slots.set(slot, take == stack.amount() ? null : new Stack(stack.material(), stack.amount() - take, true));
        }
        if (amount != 0) throw new IllegalStateException("Machine recipe consumed more than available");
    }
    private static boolean insert(List<Stack> slots, Material material, int amount) {
        // Merge first to keep outputs tidy, then use empty slots. Nothing publishes on partial fit.
        for (int pass = 0; pass < 2; pass++) for (int slot = 6; slot < 9 && amount > 0; slot++) {
            Stack before = slots.get(slot);
            if (pass == 0 && (before == null || !before.plain() || before.material() != material)) continue;
            if (pass == 1 && before != null) continue;
            int current = before == null ? 0 : before.amount();
            int put = Math.min(amount, 64 - current);
            if (put <= 0) continue;
            slots.set(slot, new Stack(material, current + put, true)); amount -= put;
        }
        return amount == 0;
    }
    private static List<Recipe> recipes() {
        ArrayList<Recipe> result = new ArrayList<>();
        result.add(new Recipe(MachineKind.CRUSHER, 0, Material.COBBLESTONE, 8, 1, Material.GRAVEL, 8));
        result.add(new Recipe(MachineKind.CRUSHER, 0, Material.GRAVEL, 8, 1, Material.SAND, 8));
        for (Material crop : List.of(Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT,
            Material.MELON_SLICE, Material.PUMPKIN))
            result.add(new Recipe(MachineKind.COMPOSTER, 1, crop, 8, 1, Material.BONE_MEAL, 4));
        for (Material seed : List.of(Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS))
            result.add(new Recipe(MachineKind.COMPOSTER, 1, seed, 16, 1, Material.BONE_MEAL, 4));
        for (Material material : Material.values()) {
            String name = material.name();
            if (!(name.endsWith("_LOG") || name.endsWith("_STEM"))) continue;
            String base = name.replace("STRIPPED_", "").replace("_LOG", "_PLANKS").replace("_STEM", "_PLANKS");
            Material planks = Material.matchMaterial(base);
            if (planks != null) result.add(new Recipe(MachineKind.SAWMILL, 2, material, 4, 1, planks, 24));
        }
        result.add(new Recipe(MachineKind.SIFTER, 3, Material.GRAVEL, 16, 2, Material.IRON_NUGGET, 4));
        result.add(new Recipe(MachineKind.SIFTER, 3, Material.SAND, 16, 2, Material.CLAY_BALL, 8));
        result.add(new Recipe(MachineKind.SIFTER, 3, Material.MUD, 16, 2, Material.RAW_COPPER, 1));
        result.add(new Recipe(MachineKind.SIFTER, 4, Material.SOUL_SAND, 16, 4, Material.QUARTZ, 2));
        result.add(new Recipe(MachineKind.SIFTER, 4, Material.RED_SAND, 16, 4, Material.RAW_GOLD, 1));
        result.add(new Recipe(MachineKind.SIFTER, 4, Material.NETHERRACK, 16, 4, Material.REDSTONE, 2));
        result.add(new Recipe(MachineKind.SIFTER, 5, Material.END_STONE, 32, 8, Material.DIAMOND, 1));
        return List.copyOf(result);
    }
    private static String title(Material material) { return material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '); }
}
