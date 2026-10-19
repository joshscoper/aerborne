package net.nerdsmp.paper.expedition;

import java.util.*;
import static net.nerdsmp.paper.expedition.ExpeditionLayout.*;

/** Revision 2 coordinates never alter the already generated revision 1 dimension. */
public final class ExpeditionPlanV2 {
    public static final int VERSION=2;
    private ExpeditionPlanV2() { }
    public static int centerX(Zone zone) { return Integer.signum(zone.x)*256; }
    public static int centerZ(Zone zone) { return Integer.signum(zone.z)*256; }
    public static int radius(Zone zone) { return zone==Zone.CITY?96:80; }
    public static Optional<Zone> zoneAt(double x,double z) {
        return Arrays.stream(Zone.values()).filter(zone->Math.abs(x-centerX(zone))<=radius(zone)+16
            && Math.abs(z-centerZ(zone))<=radius(zone)+16).findFirst();
    }
    public static final Map<Cell,Node> NODES=relocate();
    private static Map<Cell,Node> relocate() {
        Map<Cell,Node> nodes=new LinkedHashMap<>();
        for(Node old:ExpeditionLayout.NODES.values()) {
            Cell cell=new Cell(old.cell().x()-old.zone().x+centerX(old.zone()),old.cell().y(),old.cell().z()-old.zone().z+centerZ(old.zone()));
            nodes.put(cell,new Node(cell,old.material(),old.zone(),old.cooldownSeconds()));
        }
        return Collections.unmodifiableMap(nodes);
    }
}
