package net.nerdsmp.paper.item;

import java.util.*;
import net.nerdsmp.paper.item.PaperItemService.GearSlot;

/** Familiar quick-equip: reuse the same unique item slot, fill an empty slot, otherwise ask. */
public final class EquipmentChoices {
    private EquipmentChoices() { }
    public record Choice(CharacterEquipmentSlot target, List<CharacterEquipmentSlot> candidates) {
        public Choice { candidates=List.copyOf(candidates); }
        public boolean needsChoice() { return target==null; }
    }
    public static Choice choose(GearSlot type, String incomingId, Map<CharacterEquipmentSlot,String> occupied) {
        var candidates=Arrays.stream(CharacterEquipmentSlot.values()).filter(s->s.type()==type).toList();
        if(candidates.isEmpty()) throw new IllegalArgumentException("That item is not equipment.");
        if(incomingId!=null) for(var slot:candidates) if(incomingId.equals(occupied.get(slot))) return new Choice(slot,candidates);
        for(var slot:candidates) if(!occupied.containsKey(slot)) return new Choice(slot,candidates);
        return new Choice(candidates.size()==1?candidates.getFirst():null,candidates);
    }
    public static Map<String,Double> changes(Map<String,Double> before, Map<String,Double> after) {
        Map<String,Double> changes=new TreeMap<>();
        Set<String> keys=new HashSet<>(before.keySet());keys.addAll(after.keySet());
        for(String key:keys) {
            double delta=after.getOrDefault(key,0D)-before.getOrDefault(key,0D);
            if(Math.abs(delta)>0.000001) changes.put(key,delta);
        }
        return Collections.unmodifiableMap(changes);
    }
}
