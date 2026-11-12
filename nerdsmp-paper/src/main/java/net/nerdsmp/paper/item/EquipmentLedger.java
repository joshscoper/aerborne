package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.PlayerDataStore;
import java.util.*;

/** A durable intent bridges the plugin document and Minecraft's separate player inventory file. */
public final class EquipmentLedger {
    private final PlayerDataStore data;
    private final String root;
    public EquipmentLedger(PlayerDataStore data) {
        this.data=data; root="equipment";
    }
    public record Change(int slot, String before, String after) {
        public Change { if(slot<0||slot>40 || Objects.equals(before,after)) throw new IllegalArgumentException("Invalid inventory change"); }
    }
    public record Transfer(UUID player, List<Change> changes) {
        public Transfer {
            Objects.requireNonNull(player); changes=List.copyOf(changes);
            if(changes.isEmpty()||changes.size()>2||changes.stream().map(Change::slot).distinct().count()!=changes.size())
                throw new IllegalArgumentException("Invalid equipment transfer");
        }
        /** Every affected slot must still be one endpoint. Never overwrite unrelated/moved items. */
        public boolean recoverable(UUID actor, Map<Integer,String> inventory) {
            return player.equals(actor) && changes.stream().allMatch(c->Objects.equals(inventory.get(c.slot()),c.before())||Objects.equals(inventory.get(c.slot()),c.after()));
        }
    }
    public record State(Map<CharacterEquipmentSlot,String> accessories, Transfer pending) {
        public State {
            accessories=Map.copyOf(accessories);
            if(accessories.keySet().stream().anyMatch(s->!s.accessory())) throw new IllegalArgumentException("Native equipment cannot be stored twice");
        }
    }
    public State read(AccountId account) {
        return data.read(account,yaml->{
            if(!yaml.contains(root)) return new State(Map.of(),null);
            if(yaml.getInt(root+".schema")!=1) throw new IllegalStateException("Unsupported equipment record; data preserved");
            Map<CharacterEquipmentSlot,String> items=new EnumMap<>(CharacterEquipmentSlot.class);
            var section=yaml.getConfigurationSection(root+".slots");
            if(section!=null) for(String key:section.getKeys(false)) items.put(CharacterEquipmentSlot.valueOf(key),encoded(section.getString(key)));
            Transfer pending=null;
            if(yaml.contains(root+".pending")) {
                List<Change> changes=new ArrayList<>();
                var rows=yaml.getConfigurationSection(root+".pending.changes");
                if(rows==null) throw new IllegalStateException("Malformed equipment transfer");
                for(String key:rows.getKeys(false)) changes.add(new Change(Integer.parseInt(key),nullable(rows.getString(key+".before")),nullable(rows.getString(key+".after"))));
                pending=new Transfer(UUID.fromString(yaml.getString(root+".pending.player")),changes);
            }
            return new State(items,pending);
        });
    }
    public void begin(AccountId account, State expected, State next) {
        if(expected.pending()!=null||next.pending()==null) throw new IllegalStateException("An equipment transfer is already pending");
        if(!read(account).equals(expected)) throw new IllegalStateException("Equipment changed; reopen the equipment screen");
        data.update(account,yaml->{
            yaml.set(root+".schema",1); yaml.set(root+".slots",null);
            next.accessories().forEach((slot,value)->yaml.set(root+".slots."+slot.name(),encoded(value)));
            yaml.set(root+".pending.player",next.pending().player().toString());
            for(Change change:next.pending().changes()) {
                String path=root+".pending.changes."+change.slot();
                yaml.set(path+".before",change.before()==null?"":encoded(change.before()));
                yaml.set(path+".after",change.after()==null?"":encoded(change.after()));
            }
        });
    }
    public void finish(AccountId account, State expected) {
        if(expected.pending()==null||!read(account).equals(expected)) throw new IllegalStateException("Equipment transfer changed; data preserved");
        data.update(account,yaml->yaml.set(root+".pending",null));
    }
    private static String nullable(String value) { return value==null||value.isEmpty()?null:encoded(value); }
    private static String encoded(String value) {
        if(value==null||value.isEmpty()||value.length()>262144) throw new IllegalStateException("Invalid saved equipment item");
        try { Base64.getDecoder().decode(value); }
        catch(IllegalArgumentException error) { throw new IllegalStateException("Invalid saved equipment item; data preserved",error); }
        return value;
    }
}
