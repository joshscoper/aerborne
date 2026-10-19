package net.nerdsmp.verification;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.service.InMemoryEconomyService;
import net.nerdsmp.paper.service.InMemoryProgressionService;
import net.nerdsmp.paper.storage.PlayerDataStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

/** Opt-in diagnostics: writes only newly created temporary profiles, never live server data. */
public final class PersistenceFailureProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Provide the workspace build directory");
        Path build = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(build) || !build.getFileName().toString().equals("build"))
            throw new IllegalArgumentException("Only an existing build directory is accepted");
        Path root = Files.createTempDirectory(build, "persistence-probe-");
        System.out.println("Isolated data: " + root);
        boolean moneySafe = transferConservesMarks(root.resolve("transfer"));
        boolean xpSafe = failedExperienceCanBeRetried(root.resolve("experience"));
        System.out.println("Verification criteria: transfer=" + (moneySafe ? "PASS" : "FAIL")
            + ", experience=" + (xpSafe ? "PASS" : "FAIL"));
        if (!moneySafe || !xpSafe) System.exit(1);
    }

    private static PlayerDataStore store(Path root) {
        return new PlayerDataStore(root.toFile(), Logger.getAnonymousLogger());
    }

    private static AccountId bind(PlayerDataStore data, String name) {
        UUID uuid = UUID.randomUUID();
        AccountId account = new AccountId(uuid);
        data.bind(uuid, account, new PlatformIdentity(PlatformIdentity.Platform.JAVA, uuid.toString()), name);
        return account;
    }

    private static boolean transferConservesMarks(Path root) throws Exception {
        PlayerDataStore data = store(root);
        AccountId source = bind(data, "VerificationSource"), destination = bind(data, "VerificationDestination");
        InMemoryEconomyService economy = new InMemoryEconomyService(data);
        var reason = new EconomyService.TransactionReason("qa.persistence", "Isolated write-failure probe");
        economy.credit(source, 100, reason, "seed").toCompletableFuture().join();
        economy.balance(destination).toCompletableFuture().join();
        // The same injection used by existing persistence tests: an empty directory blocks the temp-file write.
        Path blocker = Files.createDirectory(data.directory().toPath().resolve(destination + ".yml.tmp"));
        boolean failed = false;
        try {
            economy.transfer(source, destination, 40, reason, "transfer-one").toCompletableFuture().join();
        } catch (RuntimeException expected) { failed = true; }
        finally { Files.delete(blocker); }
        if (!failed) throw new AssertionError("Transfer fault injection did not reach a failing save");
        InMemoryEconomyService restarted = new InMemoryEconomyService(store(root));
        long sourceAfter = restarted.balance(source).toCompletableFuture().join();
        long destinationAfter = restarted.balance(destination).toCompletableFuture().join();
        var replay = restarted.transfer(source, destination, 40, reason, "transfer-one").toCompletableFuture().join();
        long finalTotal = restarted.balance(source).toCompletableFuture().join()
            + restarted.balance(destination).toCompletableFuture().join();
        System.out.println("Transfer: funded=100; restarted source=" + sourceAfter + ", destination=" + destinationAfter
            + "; replay=" + replay.status() + "; final total=" + finalTotal);
        return finalTotal == 100;
    }

    private static boolean failedExperienceCanBeRetried(Path root) throws Exception {
        PlayerDataStore data = store(root);
        AccountId account = bind(data, "VerificationMiner");
        InMemoryProgressionService progression = new InMemoryProgressionService(data, "verification");
        progression.snapshot(account).toCompletableFuture().join();
        Path blocker = Files.createDirectory(data.directory().toPath().resolve(account + ".yml.tmp"));
        boolean failed = false;
        try {
            progression.awardExperience(account, Skill.MINING, 100, "ore-one").toCompletableFuture().join();
        } catch (RuntimeException expected) { failed = true; }
        finally { Files.delete(blocker); }
        if (!failed) throw new AssertionError("XP fault injection did not reach a failing save");
        long live = progression.snapshot(account).toCompletableFuture().join().skills().get(Skill.MINING).experience();
        var retry = progression.awardExperience(account, Skill.MINING, 100, "ore-one").toCompletableFuture().join();
        long restarted = new InMemoryProgressionService(store(root), "verification").snapshot(account)
            .toCompletableFuture().join().skills().get(Skill.MINING).experience();
        System.out.println("Experience: failed-save live XP=" + live + "; same-key retry awarded=" + retry.awarded()
            + "; restarted XP=" + restarted);
        return restarted == 100;
    }
}
