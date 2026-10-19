package net.nerdsmp.paper.studio;

import net.nerdsmp.api.model.Skill;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class RecipeBlueprintStoreTest {
    @TempDir Path temporary;
    private RecipeBlueprint recipe(){return new RecipeBlueprint("arcblade","arcblade",1,Skill.SMITHING,5,false,Collections.nCopies(9,Material.AIR)).slot(0,Material.IRON_INGOT);}
    private Path file(){return temporary.resolve("recipes.yml");}
    private RecipeBlueprintStore store()throws IOException{return new RecipeBlueprintStore(file().toFile(),r->{});}
    @Test void roundTripPreservesExactRecipeGridAndProgressionGate()throws Exception{
        RecipeBlueprintStore store=store();store.put(recipe(),null);
        assertEquals(recipe(),store().find("arcblade").orElseThrow());
        assertTrue(Files.readString(file()).contains("schema-version: 1"));
    }
    @Test void concurrentEditAndDeleteRequireTheExpectedSnapshot()throws Exception{
        RecipeBlueprintStore store=store();RecipeBlueprint r=recipe();store.put(r,null);RecipeBlueprint next=r.slot(1,Material.DIAMOND);store.put(next,r);
        assertThrows(IllegalStateException.class,()->store.put(r,r));
        assertThrows(IllegalStateException.class,()->store.remove(r.id(),r));
        assertEquals(next,store.find(r.id()).orElseThrow());store.remove(next.id(),next);assertTrue(store.find(next.id()).isEmpty());
    }
    @Test void failedWriteDoesNotPublishOrEraseRecipes()throws Exception{
        RecipeBlueprintStore initial=store();initial.put(recipe(),null);
        RecipeBlueprintStore failing=new RecipeBlueprintStore(file().toFile(),r->{},(path,text)->{throw new IOException("disk unavailable");});
        assertThrows(IOException.class,()->failing.put(recipe().slot(1,Material.DIAMOND),recipe()));
        assertEquals(recipe(),failing.find("arcblade").orElseThrow());
        assertThrows(IOException.class,()->failing.remove("arcblade",recipe()));
        assertEquals(recipe(),store().find("arcblade").orElseThrow());
    }
    @Test void outputValidatorRunsBeforeAnyWrite()throws Exception{
        RecipeBlueprintStore invalid=new RecipeBlueprintStore(file().toFile(),r->{throw new IllegalArgumentException("Unknown authored output");});
        assertThrows(IllegalArgumentException.class,()->invalid.put(recipe(),null));assertFalse(Files.exists(file()));assertTrue(invalid.all().isEmpty());
    }
    @Test void strictLoaderRejectsUnknownFieldsAliasesDuplicatesAndTypes()throws Exception{
        String[] invalid={"schema-version: 1\nrecipes: {}\nextra: true\n","schema-version: 1\nschema-version: 1\nrecipes: {}\n","schema-version: true\nrecipes: {}\n","schema-version: 1\nrecipes: &a {}\nextra: *a\n"};
        for(String yaml:invalid){Files.writeString(file(),yaml);assertThrows(IOException.class,this::store);}
    }
    @Test void emptyFileAndMissingOutputDependencyFailClosed()throws Exception{
        Files.writeString(file(),"");assertThrows(IOException.class,this::store);
        Files.delete(file());store().put(recipe(),null);
        assertThrows(IOException.class,()->new RecipeBlueprintStore(file().toFile(),r->{throw new IllegalArgumentException("Missing item");}));
    }
}
