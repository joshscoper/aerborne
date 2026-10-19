package net.nerdsmp.paper.gathering;

import net.nerdsmp.api.model.Skill;
import java.util.*;
import java.util.function.Function;

/** Loaded-cell-only, bounded tree recognition. Ambiguous connected trunks are cut manually. */
public final class ForestryRules {
    public static final int MAX_LOGS = 192;
    private ForestryRules() { }
    public record Point(int x, int y, int z) {
        public Point offset(int dx,int dy,int dz) { return new Point(x+dx,y+dy,z+dz); }
    }
    public record Cell(String material, boolean natural, boolean foliage, boolean support) { }
    public enum State { READY, NOT_TREE, TOO_LARGE, UNLOADED, AMBIGUOUS }
    public record Plan(State state, List<Point> logs, List<Point> roots, String family) {
        public Plan { logs=List.copyOf(logs); roots=List.copyOf(roots); }
    }
    public static int capacity(int level) {
        return level>=50?192:level>=35?128:level>=20?64:level>=10?32:16;
    }
    public static long experience(long configuredBase, double multiplier) {
        if(configuredBase<1 || !Double.isFinite(multiplier) || multiplier<0.1 || multiplier>10)
            throw new IllegalArgumentException("Invalid Forestry experience rate");
        return Math.max(1,Math.round(Math.min(configuredBase,1_000_000L)*multiplier));
    }
    public static String family(String material) { return GatheringRhythm.family(Skill.FORESTRY,material); }
    public static String sapling(String family) {
        return switch(family) {
            case "OAK","BIRCH","SPRUCE","JUNGLE","ACACIA","DARK_OAK","CHERRY","PALE_OAK" -> family+"_SAPLING";
            case "MANGROVE" -> "MANGROVE_PROPAGULE";
            case "CRIMSON","WARPED" -> family+"_FUNGUS";
            default -> null;
        };
    }
    public static Plan plan(Point root,Function<Point,Cell> cells,int maximum) {
        if(maximum<1 || maximum>MAX_LOGS) throw new IllegalArgumentException("Invalid felling capacity");
        Cell first=cells.apply(root), below=cells.apply(root.offset(0,-1,0));
        String family=first==null?null:family(first.material());
        if(first==null || !first.natural() || family==null || below==null || !below.support())
            return failed(State.NOT_TREE,family);
        Set<Point> seen=new HashSet<>(), logs=new LinkedHashSet<>();
        ArrayDeque<Point> queue=new ArrayDeque<>(); queue.add(root);
        boolean foliage=false;
        while(!queue.isEmpty()) {
            Point point=queue.removeFirst();
            if(!seen.add(point)) continue;
            Cell cell=cells.apply(point);
            if(cell==null) return failed(State.UNLOADED,family);
            if(!cell.natural() || !family.equals(family(cell.material()))) continue;
            if(Math.abs(point.x-root.x)>8 || Math.abs(point.z-root.z)>8 || point.y<root.y || point.y-root.y>48)
                return failed(State.TOO_LARGE,family);
            logs.add(point);
            if(logs.size()>maximum) return failed(State.TOO_LARGE,family);
            for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++) for(int dz=-1;dz<=1;dz++) {
                if(dx==0&&dy==0&&dz==0) continue;
                Point next=point.offset(dx,dy,dz); Cell adjacent=cells.apply(next);
                if(adjacent==null) return failed(State.UNLOADED,family);
                foliage|=adjacent.foliage();
                if(family.equals(family(adjacent.material()))&&adjacent.natural()&&!seen.contains(next)) queue.addLast(next);
            }
        }
        List<Point> roots=new ArrayList<>();
        for(Point log:logs) {
            Cell support=cells.apply(log.offset(0,-1,0));
            if(support!=null&&support.support()) roots.add(log);
        }
        if(logs.size()<2 || !foliage) return failed(State.NOT_TREE,family);
        if(roots.isEmpty()||roots.stream().anyMatch(p->p.y!=root.y)
            ||!(roots.size()==1||roots.size()==4&&square(roots))) return failed(State.AMBIGUOUS,family);
        List<Point> ordered=new ArrayList<>(logs);
        ordered.sort(Comparator.comparingInt(Point::y).thenComparingInt(Point::x).thenComparingInt(Point::z));
        ordered.remove(root); ordered.addFirst(root);
        roots.sort(Comparator.comparingInt(Point::x).thenComparingInt(Point::z));
        return new Plan(State.READY,ordered,roots,family);
    }
    private static boolean square(List<Point> roots) {
        return roots.stream().mapToInt(Point::x).max().orElseThrow()-roots.stream().mapToInt(Point::x).min().orElseThrow()==1
            &&roots.stream().mapToInt(Point::z).max().orElseThrow()-roots.stream().mapToInt(Point::z).min().orElseThrow()==1;
    }
    private static Plan failed(State state,String family) { return new Plan(state,List.of(),List.of(),family); }
}
