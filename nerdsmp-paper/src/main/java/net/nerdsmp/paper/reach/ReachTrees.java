package net.nerdsmp.paper.reach;

/** The builds' own trees (orchards, the tall stands over Goldenreach's farms), grown by {@link ReachTreeGen}. */
final class ReachTrees {
    private ReachTrees() { }

    /** Writes a tree into a build's canvas, never over something the build already placed. */
    static ReachTreeGen.Writer writer(ReachCanvas canvas) {
        return new ReachTreeGen.Writer() {
            @Override public boolean air(int x, int y, int z) {
                ReachCanvas.Spec spec = canvas.get(x, y, z);
                return spec == null || spec.state().equals("minecraft:air");
            }
            @Override public void set(int x, int y, int z, org.bukkit.Material material, String properties) {
                canvas.set(x, y, z, material, properties);
            }
        };
    }

    static void fruit(ReachCanvas c, int x, int y, int z, long seed) {
        ReachTreeGen.grow(writer(c), ReachTreeGen.Species.FRUIT, x, y, z, seed);
    }

    static void tallSpruce(ReachCanvas c, int x, int y, int z, int height, long seed) {
        ReachTreeGen.grow(writer(c), ReachTreeGen.Species.SPRUCE, x, y, z, seed ^ height);
    }

    static void oak(ReachCanvas c, int x, int y, int z, long seed) {
        ReachTreeGen.grow(writer(c), ReachTreeGen.Species.OAK, x, y, z, seed);
    }
}
