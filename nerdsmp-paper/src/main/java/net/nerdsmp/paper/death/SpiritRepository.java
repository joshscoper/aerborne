package net.nerdsmp.paper.death;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.bukkit.configuration.ConfigurationSection;
import java.util.UUID;

/** Save-before-publish through the canonical account store; malformed records never become living players. */
public final class SpiritRepository {
    private final PlayerDataStore data;
    private static final String ROOT="death.v1";
    public SpiritRepository(PlayerDataStore data) { this.data=data; }
    public SpiritState load(AccountId actor) {
        return data.read(actor, yaml -> {
            if (!yaml.contains(ROOT)) return SpiritState.alive();
            try {
                ConfigurationSection root=required(yaml, ROOT);
                if(root.getInt("version")!=1)throw new IllegalArgumentException("Unknown spirit schema");
                SpiritState.Soul soul=null;
                if(root.contains("soul")) {
                    var s=required(root,"soul");var a=required(s,"abilities");
                    var abilities=new SpiritState.Abilities(a.getString("mode"),bool(a,"invulnerable"),bool(a,"allow-flight"),bool(a,"flying"),point(required(a,"compass")));
                    soul=new SpiritState.Soul(UUID.fromString(s.getString("id")),point(required(s,"corpse")),point(required(s,"sanctuary")),s.getString("sanctuary-name"),bool(s,"recoverable"),number(s,"died-at"),number(s,"ready-at"),abilities);
                }
                SpiritState.Restoration restoration=null;
                if(root.contains("restoration")) {var r=required(root,"restoration");restoration=new SpiritState.Restoration(point(required(r,"destination")),r.getDouble("health-fraction",Double.NaN));}
                return new SpiritState(soul,restoration,number(root,"sickness-until"),number(root,"protected-until"));
            } catch(RuntimeException failure) {throw new IllegalStateException("Invalid spirit data for "+actor+"; refusing to discard it",failure);}
        });
    }
    public void save(AccountId actor, SpiritState state) {
        data.update(actor, yaml -> {
            yaml.set(ROOT,null);var root=yaml.createSection(ROOT);root.set("version",1);
            root.set("sickness-until",state.sicknessUntil());root.set("protected-until",state.protectedUntil());
            if(state.soul()!=null) {
                var soul=state.soul();var s=root.createSection("soul");s.set("id",soul.id().toString());write(s.createSection("corpse"),soul.corpse());write(s.createSection("sanctuary"),soul.sanctuary());
                s.set("sanctuary-name",soul.sanctuaryName());s.set("recoverable",soul.recoverable());s.set("died-at",soul.diedAt());s.set("ready-at",soul.readyAt());
                var a=s.createSection("abilities");a.set("mode",soul.abilities().mode());a.set("invulnerable",soul.abilities().invulnerable());a.set("allow-flight",soul.abilities().allowFlight());a.set("flying",soul.abilities().flying());write(a.createSection("compass"),soul.abilities().compass());
            }
            if(state.restoration()!=null) {var r=root.createSection("restoration");write(r.createSection("destination"),state.restoration().destination());r.set("health-fraction",state.restoration().healthFraction());}
        });
    }
    static SpiritState.Point point(ConfigurationSection section) {
        return new SpiritState.Point(UUID.fromString(section.getString("world")),section.getDouble("x",Double.NaN),section.getDouble("y",Double.NaN),section.getDouble("z",Double.NaN),(float)section.getDouble("yaw",Double.NaN),(float)section.getDouble("pitch",Double.NaN));
    }
    static void write(ConfigurationSection section,SpiritState.Point point) {section.set("world",point.world().toString());section.set("x",point.x());section.set("y",point.y());section.set("z",point.z());section.set("yaw",point.yaw());section.set("pitch",point.pitch());}
    static ConfigurationSection required(ConfigurationSection root,String key) {var result=root.getConfigurationSection(key);if(result==null)throw new IllegalArgumentException("Missing "+key);return result;}
    private static boolean bool(ConfigurationSection root,String key) {if(!(root.get(key) instanceof Boolean b))throw new IllegalArgumentException("Invalid "+key);return b;}
    private static long number(ConfigurationSection root,String key) {Object raw=root.get(key);if(!(raw instanceof Long || raw instanceof Integer))throw new IllegalArgumentException("Invalid "+key);return ((Number)raw).longValue();}
}
