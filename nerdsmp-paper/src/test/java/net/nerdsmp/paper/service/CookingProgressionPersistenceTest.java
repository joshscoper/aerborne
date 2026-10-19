package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.*;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class CookingProgressionPersistenceTest {
    @TempDir Path directory;
    @Test void addingCookingNeverResetsExistingSkillXpMasteriesOrTalentIds() throws Exception {
        var data=new PlayerDataStore(directory.toFile(),Logger.getAnonymousLogger());var id=AccountId.random();
        data.update(id,yaml->{yaml.set("seasons.test.progression.skills.mining.experience",40_000L);yaml.set("seasons.test.progression.masteries",List.of("MINING"));yaml.set("seasons.test.progression.talents",List.of("mining.ore_study.1"));yaml.set("unrelated.keep","yes");});
        Path file=data.directory().toPath().resolve(id+".yml");byte[] before=Files.readAllBytes(file);
        var service=new InMemoryProgressionService(data,"test");var snapshot=service.snapshot(id).toCompletableFuture().join();
        assertEquals(0,snapshot.skills().get(Skill.COOKING).experience());assertEquals(40_000,snapshot.skills().get(Skill.MINING).experience());
        assertArrayEquals(before,Files.readAllBytes(file));
        service.awardExperience(id,Skill.COOKING,10_000,"meal-fixture").toCompletableFuture().join();
        var restored=new InMemoryProgressionService(new PlayerDataStore(directory.toFile(),Logger.getAnonymousLogger()),"test").snapshot(id).toCompletableFuture().join();
        assertEquals(10,restored.skills().get(Skill.COOKING).level());assertEquals(40_000,restored.skills().get(Skill.MINING).experience());
        assertEquals(Set.of(Skill.MINING),restored.masteries());assertEquals(Set.of("mining.ore_study.1"),restored.talents());
        assertEquals("yes",data.read(id,yaml->yaml.getString("unrelated.keep")));
    }
}
