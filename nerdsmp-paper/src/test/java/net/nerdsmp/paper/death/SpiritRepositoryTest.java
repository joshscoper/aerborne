package net.nerdsmp.paper.death;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class SpiritRepositoryTest {
    @TempDir Path directory;
    final AccountId account=AccountId.random();
    PlayerDataStore data(){return new PlayerDataStore(directory.toFile(),Logger.getAnonymousLogger());}
    SpiritState ghost(){var point=new SpiritState.Point(UUID.randomUUID(),1.5,64,2.5,45,12);return new SpiritState(new SpiritState.Soul(UUID.randomUUID(),point,point,"Sanctuary",true,1000,11000,new SpiritState.Abilities("ADVENTURE",true,true,true,point)),null,0,0);}
    @Test void soulAndAbilitiesSurviveRestartExactly(){var state=ghost();new SpiritRepository(data()).save(account,state);assertEquals(state,new SpiritRepository(data()).load(account));}
    @Test void pendingRestorationIsDurableUntilAcknowledged(){var pending=ghost().resurrect(ghost().soul().corpse(),true,12000,60000,5000);new SpiritRepository(data()).save(account,pending);var loaded=new SpiritRepository(data()).load(account);assertEquals(pending,loaded);assertTrue(loaded.ghost());new SpiritRepository(data()).save(account,loaded.restored());assertFalse(new SpiritRepository(data()).load(account).ghost());assertTrue(new SpiritRepository(data()).load(account).sick(12000));}
    @Test void saveFailureLeavesSoulAndExistingNamespaceUntouched()throws Exception{var data=data();var repo=new SpiritRepository(data);var state=ghost();repo.save(account,state);data.update(account,y->y.set("equipment.keep","owned"));Files.createDirectory(directory.resolve("player_data/"+account+".yml.tmp"));assertThrows(IllegalStateException.class,()->repo.save(account,state.restored()));assertEquals(state,repo.load(account));assertEquals("owned",data.read(account,y->y.getString("equipment.keep")));assertEquals(state,new SpiritRepository(data()).load(account));}
    @Test void malformedDeathDataFailsClosedWithoutOverwritingFile()throws Exception{var data=data();data.update(account,y->{y.set("death.v1.version",1);y.set("death.v1.soul.id","broken");});byte[] before=Files.readAllBytes(directory.resolve("player_data/"+account+".yml"));assertThrows(IllegalStateException.class,()->new SpiritRepository(data).load(account));assertArrayEquals(before,Files.readAllBytes(directory.resolve("player_data/"+account+".yml")));}
    @Test void linkedClientsUseTheSameSoul(){var data=data();UUID primary=UUID.randomUUID(),linked=UUID.randomUUID();data.bind(primary,account,new PlatformIdentity(PlatformIdentity.Platform.JAVA,primary.toString()),"Primary");var repo=new SpiritRepository(data);var state=ghost();repo.save(account,state);data.bind(linked,account,new PlatformIdentity(PlatformIdentity.Platform.BEDROCK,linked.toString()),"Linked");assertEquals(state,repo.load(account));assertEquals(state,new SpiritRepository(data()).load(account));}
    @Test void untouchedPlayersAreAliveWithoutCreatingDeathData(){var data=data();assertEquals(SpiritState.alive(),new SpiritRepository(data).load(account));assertFalse(Files.exists(directory.resolve("player_data/"+account+".yml")));}
    @Test void oldGraveTipBecomesCurrentSpiritGuidance(){var tip=net.nerdsmp.paper.player.PlayerGuideService.publicTip("<gray>Physical grave recovery is free. <white>/grave recover</white> is the paid shortcut.</gray>");assertTrue(tip.contains("/spirit"));assertFalse(tip.contains("paid"));}
}
