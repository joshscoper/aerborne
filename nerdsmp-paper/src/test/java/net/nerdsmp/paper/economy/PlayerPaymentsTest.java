package net.nerdsmp.paper.economy;

import net.nerdsmp.api.model.*;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.service.InMemoryEconomyService;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class PlayerPaymentsTest {
    @TempDir Path directory;
    final AccountId source=AccountId.random(),destination=AccountId.random();
    final EconomyService.TransactionReason reason=new EconomyService.TransactionReason("test.payment","Fixture");
    PlayerDataStore data() { return new PlayerDataStore(directory.resolve("data").toFile(),Logger.getAnonymousLogger()); }
    PlayerPayments.Store store() { return new PlayerPayments.Store(directory.resolve("payments").toFile()); }
    long balance(EconomyService economy,AccountId account) { return economy.balance(account).toCompletableFuture().join(); }
    void credit(EconomyService economy,AccountId account,long amount) { economy.credit(account,amount,reason,UUID.randomUUID().toString()).toCompletableFuture().join(); }
    @Test void paymentConservesMoneyAndDuplicateReceiptsSurviveRestart() {
        var economy=new InMemoryEconomyService(data());credit(economy,source,1000);var service=new PlayerPayments(store(),economy);UUID id=UUID.randomUUID();
        assertEquals(PlayerPayments.Phase.COMPLETE,service.pay(id,source,destination,250).toCompletableFuture().join().phase());
        assertEquals(750,balance(economy,source));assertEquals(250,balance(economy,destination));
        var restoredEconomy=new InMemoryEconomyService(data());var restored=new PlayerPayments(store(),restoredEconomy);
        assertEquals(PlayerPayments.Phase.COMPLETE,restored.pay(id,source,destination,250).toCompletableFuture().join().phase());
        assertEquals(750,balance(restoredEconomy,source));assertEquals(250,balance(restoredEconomy,destination));
        assertThrows(IllegalArgumentException.class,()->restored.pay(id,source,destination,251));
    }
    @Test void crashAfterDebitBeforeReceiptTransitionNeverDebitsTwice() {
        var economy=new InMemoryEconomyService(data());credit(economy,source,1000);var faulty=new FaultStore(PlayerPayments.Phase.DEBITED);
        UUID id=UUID.randomUUID();var service=new PlayerPayments(faulty,economy);
        assertThrows(Exception.class,()->service.pay(id,source,destination,200).toCompletableFuture().join());
        assertEquals(800,balance(economy,source));assertEquals(0,balance(economy,destination));
        var recoveredEconomy=new InMemoryEconomyService(data());var recovered=new PlayerPayments(store(),recoveredEconomy);
        assertEquals(PlayerPayments.Phase.COMPLETE,recovered.resume(id).toCompletableFuture().join().phase());
        assertEquals(800,balance(recoveredEconomy,source));assertEquals(200,balance(recoveredEconomy,destination));
    }
    @Test void crashAfterCreditBeforeCompleteNeverCreditsTwice() {
        var economy=new InMemoryEconomyService(data());credit(economy,source,1000);UUID id=UUID.randomUUID();var service=new PlayerPayments(new FaultStore(PlayerPayments.Phase.COMPLETE),economy);
        assertThrows(Exception.class,()->service.pay(id,source,destination,300).toCompletableFuture().join());
        assertEquals(700,balance(economy,source));assertEquals(300,balance(economy,destination));
        var recoveredEconomy=new InMemoryEconomyService(data());var recovered=new PlayerPayments(store(),recoveredEconomy);recovered.resume(id).toCompletableFuture().join();
        assertEquals(700,balance(recoveredEconomy,source));assertEquals(300,balance(recoveredEconomy,destination));
    }
    @Test void insufficientFundsSelfPaymentsInvalidAmountsAndPreparedSaveFailureCannotMintMoney() {
        var economy=new InMemoryEconomyService();var service=new PlayerPayments(store(),economy);
        assertEquals(PlayerPayments.Phase.DECLINED,service.pay(UUID.randomUUID(),source,destination,1).toCompletableFuture().join().phase());
        for(long amount:new long[]{0,-1,PlayerPayments.MAX_PAYMENT+1}) assertThrows(IllegalArgumentException.class,()->service.pay(UUID.randomUUID(),source,destination,amount));
        assertThrows(IllegalArgumentException.class,()->service.pay(UUID.randomUUID(),source,source,1));
        credit(economy,source,100);var failed=new PlayerPayments(new FaultStore(PlayerPayments.Phase.PREPARED),economy);
        assertThrows(IllegalStateException.class,()->failed.pay(UUID.randomUUID(),source,destination,20));
        assertEquals(100,balance(economy,source));assertEquals(0,balance(economy,destination));
    }
    @Test void pendingPaymentsBlockAdditionalSendsUntilTheirReceiptRecovers() {
        var economy=new InMemoryEconomyService();credit(economy,source,100);var service=new PlayerPayments(new FaultStore(PlayerPayments.Phase.DEBITED),economy);UUID id=UUID.randomUUID();
        assertThrows(Exception.class,()->service.pay(id,source,destination,20).toCompletableFuture().join());
        assertThrows(IllegalArgumentException.class,()->service.pay(UUID.randomUUID(),source,AccountId.random(),10));
        assertEquals(PlayerPayments.Phase.COMPLETE,service.resume(id).toCompletableFuture().join().phase());
    }
    @Test void recipientOverflowLeavesRecoverableEscrowInsteadOfDuplicatingOrRefundingAmbiguously() {
        var economy=new InMemoryEconomyService();credit(economy,source,100);credit(economy,destination,Long.MAX_VALUE);var service=new PlayerPayments(store(),economy);UUID id=UUID.randomUUID();
        assertThrows(Exception.class,()->service.pay(id,source,destination,5).toCompletableFuture().join());
        assertEquals(PlayerPayments.Phase.DEBITED,service.find(id).orElseThrow().phase());assertEquals(95,balance(economy,source));
        economy.debit(destination,10,reason,"make-room").toCompletableFuture().join();service.resume(id).toCompletableFuture().join();
        assertEquals(Long.MAX_VALUE-5,balance(economy,destination));assertEquals(95,balance(economy,source));
    }
    @Test void malformedOrDuplicateFieldReceiptsFailClosedAndRemainUntouched() throws Exception {
        Path archive=directory.resolve("payments");Files.createDirectory(archive);Path file=archive.resolve(UUID.randomUUID()+".yml");
        String broken="schema: 1\nschema: 2\namount: 20\n";Files.writeString(file,broken);
        assertThrows(IllegalStateException.class,()->new PlayerPayments(store(),new InMemoryEconomyService()));assertEquals(broken,Files.readString(file));
    }
    final class FaultStore extends PlayerPayments.Store {
        final PlayerPayments.Phase phase;boolean failed;
        FaultStore(PlayerPayments.Phase phase) { super(directory.resolve("payments").toFile());this.phase=phase; }
        @Override public void save(PlayerPayments.Payment payment) { if(!failed&&payment.phase()==phase){failed=true;throw new IllegalStateException("Injected receipt save failure");}super.save(payment); }
    }
}
