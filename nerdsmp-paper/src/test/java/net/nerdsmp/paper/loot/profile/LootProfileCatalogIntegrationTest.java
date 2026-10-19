package net.nerdsmp.paper.loot.profile;

import net.nerdsmp.api.model.ItemId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static net.nerdsmp.paper.loot.profile.LootProfileDefinition.*;

class LootProfileCatalogIntegrationTest {
    @TempDir Path directory;
    @Test void publishedGearBecomesAuthorableAndActiveReferencesCannotDisappear() throws Exception {
        var store=new LootProfileStore(directory.resolve("loot.yml").toFile(),Set.of(),Set.of(),material->false);
        ItemId sword=new ItemId("storm_edge");
        var profile=new LootProfileDefinition("storm_loot",Mode.REPLACE,List.of(
            new Entry("blade",Kind.ITEM,sword.value(),.1,1,1,true,2)));
        assertThrows(IllegalArgumentException.class,()->store.put(profile));
        store.refreshItemCatalog(Set.of(sword),Set.of(sword)); store.put(profile);
        String saved=Files.readString(directory.resolve("loot.yml"));
        assertEquals(profile,store.find(profile.id()).orElseThrow());
        assertThrows(IllegalArgumentException.class,()->store.refreshItemCatalog(Set.of(),Set.of()));
        assertThrows(IllegalArgumentException.class,()->store.refreshItemCatalog(Set.of(sword),Set.of()));
        assertEquals(Set.of(sword),store.knownItems()); assertEquals(Set.of(sword),store.gearItems());
        assertEquals(saved,Files.readString(directory.resolve("loot.yml")));
    }
    @Test void mobDependenciesProtectDeleteAndInvalidExternalReloads() throws Exception {
        var file=directory.resolve("loot.yml");
        var store=new LootProfileStore(file.toFile(),Set.of(),Set.of(),material->false);
        var profile=new LootProfileDefinition("crypt_rewards",Mode.ADDITIVE,List.of());
        store.put(profile);
        AtomicReference<Set<String>> references=new AtomicReference<>(Set.of(profile.id()));
        store.externalReferences(references::get);
        String saved=Files.readString(file);
        assertThrows(IllegalArgumentException.class,()->store.remove(profile.id()));
        assertEquals(saved,Files.readString(file));
        var replacement=new LootProfileStore(directory.resolve("empty.yml").toFile(),Set.of(),Set.of(),material->false);
        replacement.put(new LootProfileDefinition("other_rewards",Mode.ADDITIVE,List.of()));
        Files.writeString(file,Files.readString(directory.resolve("empty.yml")));
        assertThrows(java.io.IOException.class,store::reload);
        assertEquals(List.of(profile),store.all());
        references.set(Set.of()); store.remove(profile.id()); assertTrue(store.all().isEmpty());
    }
    @Test void invalidDependencyProviderIsNotInstalled() throws Exception {
        var store=new LootProfileStore(directory.resolve("loot.yml").toFile(),Set.of(),Set.of(),material->false);
        store.put(new LootProfileDefinition("test",Mode.ADDITIVE,List.of()));
        assertThrows(IllegalArgumentException.class,()->store.externalReferences(()->Set.of("missing")));
        store.remove("test"); assertTrue(store.all().isEmpty());
    }
}
