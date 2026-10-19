package net.nerdsmp.paper.gathering;

import net.nerdsmp.api.model.Skill;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Bounded, session-only reward for deliberate, varied harvesting; never establishes resource eligibility. */
public final class GatheringRhythm {
    public static final int MAX_SESSIONS=2_048;
    public static final int MAX_CHAIN=20;
    public static final int MAX_VARIETIES=4;
    public static final int HISTORY=12;
    public static final long MIN_INTERVAL_MILLIS=100;
    private record Key(UUID player,Skill skill) { }
    private static final class Session {
        final UUID world;
        final ArrayDeque<String> recent=new ArrayDeque<>(),operations=new ArrayDeque<>();
        long lastAt;
        int chain;
        Session(UUID world,long now) { this.world=world; this.lastAt=now-MIN_INTERVAL_MILLIS; }
    }
    public record Result(int chain,int varieties,double bonusPercent,boolean milestone) {
        public static final Result NONE=new Result(0,0,0,false);
    }
    private final LinkedHashMap<Key,Session> sessions=new LinkedHashMap<>(32,.75f,true);

    /** Call once, only after a successful provenance claim for a completed mature/natural harvest. */
    public Result record(UUID player,UUID world,Skill skill,String material,String operation,long now) {
        if(player==null||world==null||operation==null||operation.isBlank()) return Result.NONE;
        String family=family(skill,material);
        if(family==null) return Result.NONE;
        Key key=new Key(player,skill);
        Session session=sessions.get(key);
        if(session==null||!world.equals(session.world)||now<session.lastAt||now-session.lastAt>window(skill)) {
            session=new Session(world,now); sessions.put(key,session);
        }
        if(session.operations.contains(operation)||now-session.lastAt<MIN_INTERVAL_MILLIS) return Result.NONE;
        session.lastAt=now;
        int previousChain=session.chain;
        session.chain=Math.min(MAX_CHAIN,session.chain+1);
        session.recent.addLast(family); if(session.recent.size()>HISTORY) session.recent.removeFirst();
        session.operations.addLast(operation); if(session.operations.size()>HISTORY) session.operations.removeFirst();
        while(sessions.size()>MAX_SESSIONS) sessions.pollFirstEntry();
        int variety=Math.min(MAX_VARIETIES,new HashSet<>(session.recent).size());
        double bonus=(session.chain/5)*2.0+(variety-1)*4.0;
        boolean milestone=session.chain>previousChain&&session.chain%5==0;
        return new Result(session.chain,variety,Math.min(20,bonus),milestone);
    }

    public void clear(UUID player) { sessions.keySet().removeIf(key->key.player().equals(player)); }
    public int size() { return sessions.size(); }
    public static long window(Skill skill) { return skill==Skill.FORESTRY?60_000:12_000; }
    public static String family(Skill skill,String material) {
        if(material==null) return null;
        if(skill==Skill.FARMING) return switch(material) {
            case "WHEAT","CARROTS","POTATOES","BEETROOTS","NETHER_WART","COCOA","SWEET_BERRY_BUSH","PITCHER_CROP" -> material;
            default -> null;
        };
        if(skill!=Skill.FORESTRY) return null;
        String name=material.startsWith("STRIPPED_")?material.substring(9):material;
        if(name.endsWith("_LOG")) return name.substring(0,name.length()-4);
        if(name.endsWith("_HYPHAE")) return name.substring(0,name.length()-7);
        if(name.equals("CRIMSON_STEM")||name.equals("WARPED_STEM")) return name.substring(0,name.length()-5);
        return null;
    }
}
