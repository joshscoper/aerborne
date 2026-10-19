package net.nerdsmp.paper.gui;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Wiring guard; actual metadata preservation is checked on the isolated Paper server. */
class ForgeRepairPresentationTest {
    @Test void displayingTheForgeDoesNotRestyleItsInputBeforeRepair() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/nerdsmp/paper/gui/MenuManager.java"));
        int start = source.indexOf("private void renderForge(MenuHolder holder, Player player)");
        int end = source.indexOf("private void renderForgeRepairs", start);
        assertTrue(start >= 0 && end > start);
        String render = source.substring(start, end);
        assertFalse(render.contains(".refresh(gear)"));
        assertFalse(render.contains("gear.setItemMeta("));
        assertTrue(render.contains("renderForgeRepairs(menu, player, gear)"));
    }
}
