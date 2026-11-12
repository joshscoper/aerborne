package net.aerborne.client.fabric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Native screen for menus where slot position is the point: the bank vault, a player trade (two offers side by side),
 * the character equipment doll and expedition loot. The server's grid is kept cell for cell on larger slots; filler
 * glass disappears, its named placeholders become empty or locked cells, and the bottom row becomes footer buttons.
 * Your bag sits below. Clicks are the vanilla ones (left, right, shift, keys 1–9 to swap with the hotbar), each sent to
 * the server's own slot, so every rule stays server-side.
 */
public final class StorageMenuScreen extends Screen {
    private static final Identifier TOOLTIP = Identifier.fromNamespaceAndPath("aerborne_client", "menu");
    private static final int CELL = 22, BAG_CELL = 18, BAG = 96;

    private final ContainerScreen chest;
    private final ChestMenu menu;
    private final int rows, chestSlots;
    private final boolean trade, bank;
    /** Rows of the grid that are drawn (fully filler rows at the top and bottom are trimmed). */
    private int firstRow, lastRow, gridRows;
    private boolean footerRow;
    private float scale = 1;
    private int left, top, windowWidth, windowHeight, gridX, gridY, bagY;
    private List<ItemStack> footerSnapshot = List.of();

    StorageMenuScreen(ContainerScreen chest, String kind) {
        super(chest.getTitle());
        this.chest = chest; menu = chest.getMenu(); rows = menu.getRowCount(); chestSlots = rows * 9;
        trade = kind.contains("TradeManager");
        bank = kind.contains("Bank");
    }

    private static List<String> lore(ItemStack item) {
        var lore = item.get(DataComponents.LORE);
        if (lore == null) return List.of();
        return lore.lines().stream().map(Component::getString).filter(s -> !s.isBlank()).toList();
    }

    private static boolean pane(ItemStack item) {
        return item.getItem().builtInRegistryHolder().key().identifier().getPath().endsWith("glass_pane");
    }

    /** What a cell shows. */
    private enum Cell { HIDDEN, EMPTY, LOCKED, ITEM }

    private Cell cell(int slot) {
        ItemStack item = menu.slots.get(slot).getItem();
        if (item.isEmpty()) return Cell.EMPTY;
        if (AerborneMenus.filler(item)) return Cell.HIDDEN;
        if (pane(item)) {
            String name = item.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (name.startsWith("locked")) return Cell.LOCKED;
            if (name.startsWith("empty")) return Cell.EMPTY;
            if (name.startsWith("not used") || name.strip().length() <= 2) return Cell.HIDDEN;
        }
        return Cell.ITEM;
    }

    @Override protected void init() {
        // The bottom row of a tall menu holds its controls; a short loot window is all grid.
        footerRow = rows >= 4;
        int gridEnd = footerRow ? rows - 1 : rows;
        firstRow = 0; lastRow = gridEnd - 1;
        while (firstRow < lastRow && rowHidden(firstRow)) firstRow++;
        while (lastRow > firstRow && rowHidden(lastRow)) lastRow--;
        gridRows = lastRow - firstRow + 1;
        List<Integer> footer = new ArrayList<>();
        if (footerRow) for (int i = chestSlots - 9; i < chestSlots; i++) if (cell(i) == Cell.ITEM) footer.add(i);
        int footerRows = (footer.size() + 2) / 3;
        windowWidth = Math.max(9 * CELL + 40, 420);
        windowHeight = 30 + gridRows * CELL + 10 + footerRows * 24 + 8 + BAG + 10;
        int realWidth = minecraft.getWindow().getGuiScaledWidth(), realHeight = minecraft.getWindow().getGuiScaledHeight();
        scale = Math.min(1F, Math.min((realWidth - 8) / (float) windowWidth, (realHeight - 8) / (float) windowHeight));
        width = Math.round(realWidth / scale); height = Math.round(realHeight / scale);
        left = (width - windowWidth) / 2; top = (height - windowHeight) / 2;
        gridX = left + (windowWidth - 9 * CELL) / 2; gridY = top + 30;
        int footerY = gridY + gridRows * CELL + 10;
        int buttonWidth = (windowWidth - 28 - 2 * 6) / 3;
        for (int k = 0; k < footer.size(); k++) {
            int slot = footer.get(k);
            addRenderableWidget(new FooterButton(slot, left + 14 + k % 3 * (buttonWidth + 6), footerY + k / 3 * 24, buttonWidth));
        }
        bagY = footerY + footerRows * 24 + 14;
        addRenderableWidget(new AerborneMenuScreen.SpriteButton(left + windowWidth - 110, top + windowHeight - 28, 96, 20,
                Component.literal("Classic view"), null, 0, false, b -> classic()));
        footerSnapshot = footerItems();
    }

    private boolean rowHidden(int row) {
        for (int c = 0; c < 9; c++) if (cell(row * 9 + c) != Cell.HIDDEN) return false;
        return true;
    }

    private List<ItemStack> footerItems() {
        var items = new ArrayList<ItemStack>();
        if (footerRow) for (int i = chestSlots - 9; i < chestSlots; i++) items.add(menu.slots.get(i).getItem().copy());
        return items;
    }

    @Override public void tick() {
        super.tick();
        if (minecraft.player != null && minecraft.player.containerMenu != menu) return;
        var now = footerItems();
        for (int i = 0; i < now.size(); i++) if (i >= footerSnapshot.size() || !ItemStack.matches(now.get(i), footerSnapshot.get(i))) { rebuildWidgets(); return; }
    }

    // ---- Hit testing ----

    /** The menu slot under the cursor (grid or bag), or -1. */
    private int slotAt(double mx, double my) {
        int x = (int) Math.floor(mx), y = (int) Math.floor(my);
        if (x >= gridX && x < gridX + 9 * CELL && y >= gridY && y < gridY + gridRows * CELL) {
            int slot = (firstRow + (y - gridY) / CELL) * 9 + (x - gridX) / CELL;
            return cell(slot) == Cell.HIDDEN ? -1 : slot;
        }
        int bx = bagX();
        if (x < bx || x >= bx + 9 * BAG_CELL) return -1;
        int column = (x - bx) / BAG_CELL, dy = y - bagY;
        if (dy >= 0 && dy < 3 * BAG_CELL) return chestSlots + dy / BAG_CELL * 9 + column;
        if (dy >= 3 * BAG_CELL + 4 && dy < 4 * BAG_CELL + 4) return chestSlots + 27 + column;
        return -1;
    }

    private int bagX() { return left + 20; }

    private void send(int slot, int button, ContainerInput input) {
        if (minecraft.player == null || minecraft.gameMode == null || minecraft.player.containerMenu != menu) return;
        if (AerborneMenus.locked(menu.slots.get(slot))) return;
        minecraft.gameMode.handleContainerInput(menu.containerId, slot, button, input, minecraft.player);
    }

    // ---- Input ----

    private MouseButtonEvent canvas(MouseButtonEvent e) { return new MouseButtonEvent(e.x() / scale, e.y() / scale, e.buttonInfo()); }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        var c = canvas(e);
        if (super.mouseClicked(c, twice)) return true;
        int slot = slotAt(c.x(), c.y());
        if (slot >= 0 && (c.button() == 0 || c.button() == 1)) {
            // A double click gathers matching stacks onto the cursor, as in the vanilla screen.
            if (twice && c.button() == 0 && !menu.getCarried().isEmpty() && !c.hasShiftDown()) send(slot, 0, ContainerInput.PICKUP_ALL);
            else send(slot, c.button(), c.hasShiftDown() ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP);
            return true;
        }
        return false;
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) { return super.mouseReleased(canvas(e)); }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return super.mouseDragged(canvas(e), dx / scale, dy / scale); }

    private double mouseX() { return minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()) / scale; }
    private double mouseY() { return minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()) / scale; }

    @Override public boolean keyPressed(KeyEvent e) {
        int slot = slotAt(mouseX(), mouseY());
        if (slot >= 0 && e.key() >= GLFW.GLFW_KEY_1 && e.key() <= GLFW.GLFW_KEY_9) {
            // Number keys swap the hovered slot with that hotbar slot.
            send(slot, e.key() - GLFW.GLFW_KEY_1, ContainerInput.SWAP);
            return true;
        }
        if (slot >= 0 && minecraft.options.keyDrop.matches(e)) { send(slot, e.hasControlDown() ? 1 : 0, ContainerInput.THROW); return true; }
        if (slot >= 0 && e.key() == GLFW.GLFW_KEY_L && slot >= chestSlots) {
            AerborneMenus.toggleLock(menu.slots.get(slot)); return true;
        }
        if (minecraft.options.keyInventory.matches(e)) { onClose(); return true; }
        return super.keyPressed(e);
    }

    private void classic() { AerborneMenus.CLASSIC.put(chest, true); minecraft.gui.setScreen(chest); }
    @Override public void onClose() { if (minecraft.player != null) minecraft.player.closeContainer(); else super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }

    // ---- Drawing ----

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) { g.fill(0, 0, width, height, 0x90000000); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.pose().pushMatrix(); g.pose().scale(scale, scale);
        int mx = Math.round(mouseX / scale), my = Math.round(mouseY / scale);
        Ui.window(g, left, top, windowWidth, windowHeight);
        Ui.windowTitle(g, font, title.getString(), left, top, windowWidth, 0);
        int hovered = slotAt(mx, my);
        Ui.inset(g, gridX - 6, gridY - 6, 9 * CELL + 12, gridRows * CELL + 12);
        if (trade) {
            // Two offers: a gold seam between the left and right grids.
            int seam = gridX + 4 * CELL + CELL / 2;
            g.fill(seam - 1, gridY + 2, seam + 1, gridY + gridRows * CELL - 2, 0x80F4C76B);
        }
        for (int r = firstRow; r <= lastRow; r++) for (int c = 0; c < 9; c++) {
            int slot = r * 9 + c;
            int x = gridX + c * CELL, y = gridY + (r - firstRow) * CELL;
            drawCell(g, slot, x, y, CELL, slot == hovered);
        }
        drawBag(g, hovered);
        super.extractRenderState(g, mx, my, delta);
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            g.item(carried, mx - 8, my - 8);
            g.itemDecorations(font, carried, mx - 8, my - 8);
        } else if (hovered >= 0) {
            ItemStack item = menu.slots.get(hovered).getItem();
            if (!item.isEmpty()) g.setTooltipForNextFrame(font, Screen.getTooltipFromItem(minecraft, item), item.getTooltipImage(), mx, my, TOOLTIP);
        }
        g.pose().popMatrix();
    }

    private void drawCell(GuiGraphicsExtractor g, int slot, int x, int y, int size, boolean hover) {
        Cell kind = cell(slot);
        if (kind == Cell.HIDDEN) return;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? Ui.SLOT_HOVER : Ui.SLOT, x, y, size, size);
        int inset = (size - 16) / 2;
        if (kind == Cell.LOCKED) {
            g.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0x900D1A2B);
            Ui.icon(g, Ui.control("padlock_closed"), x + inset + 1, y + inset + 1, 14);
            return;
        }
        if (kind == Cell.EMPTY) return;
        ItemStack item = menu.slots.get(slot).getItem();
        String rarity = AerborneMenus.rarityName(item);
        if (rarity != null && ClientState.preferences.rarity) Ui.icon(g, Ui.rarity(rarity), x, y, size);
        g.item(item, x + inset, y + inset);
        g.itemDecorations(font, item, x + inset, y + inset);
        if (AerborneMenus.locked(menu.slots.get(slot))) AerborneMenus.lockBadge(g, x + inset, y + inset);
    }

    private void drawBag(GuiGraphicsExtractor g, int hovered) {
        int x0 = bagX(), y0 = bagY;
        Ui.inset(g, x0 - 6, y0 - 8, 9 * BAG_CELL + 12, 4 * BAG_CELL + 16);
        int held = minecraft.player == null ? -1 : minecraft.player.getInventory().getSelectedSlot();
        for (int k = 0; k < 36; k++) {
            int x = x0 + k % 9 * BAG_CELL, y = k < 27 ? y0 + k / 9 * BAG_CELL : y0 + 3 * BAG_CELL + 4;
            int slot = chestSlots + k;
            drawCell(g, slot, x, y, BAG_CELL, slot == hovered);
            if (k >= 27 && k - 27 == held) g.outline(x, y, BAG_CELL, BAG_CELL, Ui.GOLD);
        }
        int tx = x0 + 9 * BAG_CELL + 16, tw = left + windowWidth - 14 - tx;
        g.text(font, Ui.heading("Your bag"), tx, y0 - 4, Ui.GOLD, true);
        List<String> help = new ArrayList<>();
        if (bank) { help.add("Click or shift-click to store."); help.add("Locked cells open with Marks."); }
        else if (trade) { help.add("Click a stack to offer it."); help.add("Any change clears both confirms."); }
        else help.add("Shift-click moves a stack across.");
        help.add("Keys 1–9 swap with your hotbar.");
        help.add("L locks a bag slot.");
        int y = y0 + 10;
        for (String line : help) for (var wrapped : font.split(Component.literal(line), tw)) { g.text(font, wrapped, tx, y, Ui.MUTED, false); y += 10; }
    }

    /** A control from the bottom row: its own icon (or ours for back, close and pages) and its name. */
    private final class FooterButton extends Button {
        private final int slot;
        FooterButton(int slot, int x, int y, int w) {
            super(x, y, w, 20, menu.slots.get(slot).getItem().getHoverName(), b -> {}, DEFAULT_NARRATION);
            this.slot = slot;
        }
        @Override public void onPress(InputWithModifiers input) { send(slot, 0, input.hasShiftDown() ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP); }
        @Override protected boolean isValidClickButton(net.minecraft.client.input.MouseButtonInfo button) { return button.button() == 0 || button.button() == 1; }
        @Override public void onClick(MouseButtonEvent e, boolean twice) { send(slot, e.button(), e.hasShiftDown() ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP); }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
            ItemStack item = menu.slots.get(slot).getItem();
            boolean focus = isHovered();
            String name = item.getHoverName().getString(), lower = name.toLowerCase(Locale.ROOT);
            // A confirmed trade side reads green; anything waiting on you reads gold.
            boolean done = lore(item).stream().anyMatch(l -> l.startsWith("Any change")) || lower.endsWith("confirmed");
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Ui.BUTTON.get(true, focus), getX(), getY(), getWidth(), getHeight());
            if (done) g.outline(getX(), getY(), getWidth(), getHeight(), 0xFF70DB9B);
            Identifier icon = lower.contains("close") || lower.startsWith("cancel") ? Ui.control("close") : lower.startsWith("← back") || lower.equals("back") ? Ui.control("back")
                    : lower.contains("previous") ? Ui.control("page_left") : lower.contains("next") ? Ui.control("page_right") : null;
            String label = name.replace("←", "").replace("→", "").strip();
            if (label.toLowerCase(Locale.ROOT).startsWith("back")) label = "Back";
            var text = font.plainSubstrByWidth(label, getWidth() - 26);
            int total = 16 + font.width(text), x = getX() + (getWidth() - total) / 2;
            if (icon != null) Ui.icon(g, icon, x, getY() + 3, 14);
            else { g.pose().pushMatrix(); g.pose().translate(x, getY() + 3); g.pose().scale(0.875F, 0.875F); g.item(item, 0, 0); g.pose().popMatrix(); }
            g.text(font, text, x + 16, getY() + 6, done ? 0xFF70DB9B : focus ? Ui.GOLD_LIGHT : Ui.TEXT, true);
            if (focus && menu.getCarried().isEmpty()) {
                // Footer buttons keep the item's full tooltip (costs, who can press it).
                g.setTooltipForNextFrame(font, Screen.getTooltipFromItem(minecraft, item), item.getTooltipImage(), mx, my, TOOLTIP);
            }
        }
    }
}
