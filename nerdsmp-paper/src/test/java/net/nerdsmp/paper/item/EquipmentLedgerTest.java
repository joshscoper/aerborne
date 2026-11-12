package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentLedgerTest {
    @TempDir Path directory;
    private final AccountId account=AccountId.random();
    private final UUID player=UUID.randomUUID();
    private static String item(String name) {return Base64.getEncoder().encodeToString(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private PlayerDataStore data() {return new PlayerDataStore(directory.toFile(),Logger.getAnonymousLogger());}
    private EquipmentLedger.State intent() {
        return new EquipmentLedger.State(Map.of(CharacterEquipmentSlot.RING_1,item("ring")),
            new EquipmentLedger.Transfer(player,List.of(new EquipmentLedger.Change(9,item("ring"),null))));
    }
    @Test void intentSurvivesRestartAndFinishesWithoutDuplicatingNativeGear() {
        var ledger=new EquipmentLedger(data()); var old=ledger.read(account);
        assertTrue(old.accessories().isEmpty()); ledger.begin(account,old,intent());
        var restarted=new EquipmentLedger(data()); assertEquals(intent(),restarted.read(account));
        restarted.finish(account,intent());
        assertEquals(new EquipmentLedger.State(intent().accessories(),null),new EquipmentLedger(data()).read(account));
        assertEquals(intent().accessories(),new EquipmentLedger(data()).read(account).accessories());
        assertThrows(IllegalArgumentException.class,()->new EquipmentLedger.State(Map.of(CharacterEquipmentSlot.HEAD,item("helm")),null));
    }
    @Test void recoveryAcceptsBeforeAfterAndPartlyAppliedSwapsButRefusesDivergentOrDifferentPlayers() {
        var transfer=new EquipmentLedger.Transfer(player,List.of(new EquipmentLedger.Change(9,item("sword"),item("old")),new EquipmentLedger.Change(0,item("old"),item("sword"))));
        assertTrue(transfer.recoverable(player,Map.of(9,item("sword"),0,item("old"))));
        assertTrue(transfer.recoverable(player,Map.of(9,item("old"),0,item("sword"))));
        assertTrue(transfer.recoverable(player,Map.of(9,item("old"),0,item("old"))));
        assertFalse(transfer.recoverable(player,Map.of(9,item("unrelated"),0,item("old"))));
        assertFalse(transfer.recoverable(UUID.randomUUID(),Map.of(9,item("sword"),0,item("old"))));
    }
    @Test void staleOrRepeatedRequestsCannotReplaceAnotherPendingTransfer() {
        var ledger=new EquipmentLedger(data());var old=ledger.read(account);ledger.begin(account,old,intent());
        assertThrows(IllegalStateException.class,()->ledger.begin(account,old,intent()));
        assertThrows(IllegalStateException.class,()->ledger.begin(account,intent(),intent()));
        assertEquals(intent(),ledger.read(account));
    }
    @Test void failedSavePublishesNoEquipmentIntentAndCanBeRetried() throws Exception {
        var store=data();var ledger=new EquipmentLedger(store);var old=ledger.read(account);
        Path blocked=store.directory().toPath().resolve(account+".yml");Files.createDirectory(blocked);Files.createFile(blocked.resolve("keep"));
        assertThrows(IllegalStateException.class,()->ledger.begin(account,old,intent()));assertEquals(old,ledger.read(account));
        Files.delete(blocked.resolve("keep"));Files.delete(blocked);
        ledger.begin(account,old,intent());assertEquals(intent(),ledger.read(account));
    }
    @Test void malformedSavedItemsArePreservedAndRejected() {
        var store=data();store.update(account,yaml->{yaml.set("equipment.schema",1);yaml.set("equipment.slots.RING_1","bad!base64");});
        assertThrows(IllegalStateException.class,()->new EquipmentLedger(store).read(account));
        assertEquals("bad!base64",store.read(account,yaml->yaml.getString("equipment.slots.RING_1")));
    }
    @Test void characterLayoutContainsSixNativeAndTenVirtualSlotsWithoutFooterCollisions() {
        Set<Integer> used=new HashSet<>(Set.of(0,4,8,48,49,51));
        assertEquals(16,CharacterEquipmentSlot.values().length);
        assertEquals(10,Arrays.stream(CharacterEquipmentSlot.values()).filter(CharacterEquipmentSlot::accessory).count());
        for(var slot:CharacterEquipmentSlot.values()) assertTrue(used.add(slot.menuSlot()),slot.title());
    }
}
