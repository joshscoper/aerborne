package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryRepositoryTest {
    @TempDir Path directory;
    final AccountId account = AccountId.random();
    PlayerDataStore data() { return new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger()); }
    @Test void bonusAndAbsoluteExpirySurviveRestart() {
        var food = new RecoveryRules.WellFed(12345678900L, 40);
        new RecoveryRepository(data()).save(account, food);
        assertEquals(food, new RecoveryRepository(data()).load(account));
        new RecoveryRepository(data()).save(account, RecoveryRules.WellFed.NONE);
        assertEquals(RecoveryRules.WellFed.NONE, new RecoveryRepository(data()).load(account));
    }
    @Test void untouchedPlayersNeedNoNewFile() {
        assertEquals(RecoveryRules.WellFed.NONE, new RecoveryRepository(data()).load(account));
        assertFalse(Files.exists(directory.resolve("player_data/" + account + ".yml")));
    }
    @Test void saveFailurePreservesExistingBonusAndOtherSystems() throws Exception {
        var data = data(); var repository = new RecoveryRepository(data);
        var before = new RecoveryRules.WellFed(10000, 25); repository.save(account, before);
        data.update(account, yaml -> yaml.set("equipment.keep", "owned"));
        Files.createDirectory(directory.resolve("player_data/" + account + ".yml.tmp"));
        assertThrows(IllegalStateException.class, () -> repository.save(account, RecoveryRules.WellFed.NONE));
        assertEquals(before, repository.load(account));
        assertEquals("owned", data.read(account, yaml -> yaml.getString("equipment.keep")));
    }
    @Test void malformedStateFailsWithoutRewriting() throws Exception {
        var data = data(); data.update(account, yaml -> yaml.set("food.v1.until", "bad"));
        var path = directory.resolve("player_data/" + account + ".yml"); byte[] before = Files.readAllBytes(path);
        assertThrows(IllegalStateException.class, () -> new RecoveryRepository(data).load(account));
        assertArrayEquals(before, Files.readAllBytes(path));
    }
    @Test void linkedClientsShareCanonicalFoodState() {
        var data = data(); UUID primary = UUID.randomUUID(), linked = UUID.randomUUID();
        data.bind(primary, account, new PlatformIdentity(PlatformIdentity.Platform.JAVA, primary.toString()), "Primary");
        var food = new RecoveryRules.WellFed(100000, 40); new RecoveryRepository(data).save(account, food);
        data.bind(linked, account, new PlatformIdentity(PlatformIdentity.Platform.BEDROCK, linked.toString()), "Linked");
        assertEquals(food, new RecoveryRepository(data).load(account));
    }
}
