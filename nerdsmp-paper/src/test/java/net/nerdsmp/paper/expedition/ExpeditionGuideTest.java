package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.loot.RewardDropService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExpeditionGuideTest {
    @Test void dropChancesAndAmountsReadNaturally() {
        assertEquals("100%", ExpeditionBestiary.percent(1.0));
        assertEquals("35%", ExpeditionBestiary.percent(0.35));
        assertEquals("1.5%", ExpeditionBestiary.percent(0.015));
        assertEquals("8%", ExpeditionBestiary.percent(0.08));
        assertEquals("0.80%", ExpeditionBestiary.percent(0.008));
        assertEquals("1–3", ExpeditionBestiary.amount(new RewardDropService.DropLine("Iron nugget", .14, 1, 3)));
        assertEquals("", ExpeditionBestiary.amount(new RewardDropService.DropLine("Relic", .35, 1, 1)));
        assertEquals("×2", ExpeditionBestiary.amount(new RewardDropService.DropLine("Sealed gear", 1, 2, 2)));
    }

    @Test void theShippedSamplesAreNewerThanEveryVersionTheyReplace() throws IOException {
        for (String sample : ExpeditionService.SAMPLES) {
            Path file = Path.of("src/main/resources/expeditions/" + sample + ".yml");
            String current = ExpeditionService.sha256(file.toFile());
            assertFalse(current.isEmpty());
            assertFalse(ExpeditionService.PREVIOUS_SAMPLES.get(sample).contains(current),
                sample + " would be replaced by itself on every start; add the old hash only after changing the file");
        }
    }

    @Test void sampleFingerprintsIgnoreLineEndings(@org.junit.jupiter.api.io.TempDir Path folder) throws IOException {
        Path lf = folder.resolve("lf.yml"), crlf = folder.resolve("crlf.yml");
        Files.writeString(lf, "title: X\nlevel: 8\n");
        Files.writeString(crlf, "title: X\r\nlevel: 8\r\n");
        assertEquals(ExpeditionService.sha256(lf.toFile()), ExpeditionService.sha256(crlf.toFile()));
    }
}
