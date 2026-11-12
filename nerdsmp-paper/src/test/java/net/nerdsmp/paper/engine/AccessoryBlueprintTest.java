package net.nerdsmp.paper.engine;

import net.nerdsmp.paper.item.PaperItemService.GearSlot;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AccessoryBlueprintTest {
    @TempDir Path directory;
    private ItemBlueprint ring(GearSlot slot) {return new ItemBlueprint("test_ring","Test Ring",Material.GOLD_NUGGET,"UNCOMMON",1,5,null,List.of(),Map.of("defense",1D),slot);}
    @Test void explicitAccessorySlotRoundTripsAndCannotChangeAfterPublication() throws Exception {
        Path file=directory.resolve("items.yml");var store=new ItemBlueprintStore(file.toFile(),Set.of(),Set.of(),m->true,(path,text)->Files.writeString(path,text));
        store.put(ring(GearSlot.RING),null);
        var restored=new ItemBlueprintStore(file.toFile(),Set.of(),Set.of(),m->true,(path,text)->Files.writeString(path,text));
        assertEquals(ring(GearSlot.RING),restored.find("test_ring").orElseThrow());
        assertThrows(IllegalArgumentException.class,()->store.put(ring(GearSlot.NECK),ring(GearSlot.RING)));
    }
    @Test void oldDefinitionsStillInferNativeSlotsAndAccessoriesCannotBecomeWeaponsByAppearance() {
        assertEquals(GearSlot.HEAD,new ItemBlueprint("helm","Helm",Material.IRON_HELMET,"COMMON",0,1,null,List.of(),Map.of()).slot());
        assertThrows(IllegalArgumentException.class,()->new ItemBlueprint("ring","Ring",Material.IRON_SWORD,"COMMON",0,1,null,List.of(),Map.of(),GearSlot.RING));
        assertThrows(IllegalArgumentException.class,()->new ItemBlueprint("ring","Ring",Material.GOLD_NUGGET,"COMMON",0,1,null,List.of(),Map.of("damage",3D),GearSlot.RING));
    }
}
