package net.aerborne.client.fabric;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.aerborne.client.protocol.MenuLayout;
import net.aerborne.client.protocol.IslandTalentGraph;
import net.aerborne.client.protocol.TalentGraph;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Native presentation of server slots. Transactions, confirmations and contents remain server-owned: every click goes
 * to the slot it came from, and a click on an item that changed since it was drawn is refused.
 *
 * <p>The chest is read into groups. <b>Header</b> items (a shop's sign and the merchant's reputation) and <b>chips</b>
 * (tabs, filters, quantities; the active one lit in gold) sit under the title. <b>Navigation</b> (anything on the
 * bottom row: back, close, pages, refresh) runs along the foot. <b>Sockets</b> are the empty input slots of a station
 * such as the forge; you place items into them from your <b>bag</b>, which stations show under the menu. Everything
 * else is <b>content</b>: cards with an icon, a name and the two lines that matter most (prices first), or, when the
 * content is a ranking ("#1 Name"), a table. Talent trees keep their shape.
 */
public final class NativeMenuScreen extends Screen {
    private static final Identifier TOOLTIP = Identifier.fromNamespaceAndPath("aerborne_client", "menu");
    private static final Pattern RANK = Pattern.compile("^#(\\d+)\\s+(.*)$"), QUANTITY = Pattern.compile("^(Buy|Sell) \\d+$"),
            PRICE = Pattern.compile("^Sell\\s+[\\d,]+.*");
    /** "Completed 2/37", "Progress 3/10", "▮▮▮▯▯ 3/10": a card with progress gets a bar. */
    private static final Pattern PROGRESS = Pattern.compile("^(?:Progress|Completed|[■□▮▯█░▌|\\s]+).*?([\\d,]+)\\s*/\\s*([\\d,]+).*$");
    /** The order key lines are shown in: what it costs or pays first. */
    private static final List<String> KEY_ORDER = List.of("Buy", "Sell", "Price", "Cost", "Total", "Payout", "High bid", "Starting bid", "Time left");
    /** Detail lines worth showing on a card before any description. */
    private static final Pattern KEY_LINE = Pattern.compile("^(Buy|Sell|Total|Payout|Cost|Price|Reward|Rewards|High bid|Starting bid|"
            + "Time left|Seller|Status|Bundle|Requires|Balance|Progress|Completed|Level)\\b.*");
    private static final int SILVER = 0xFFD5DCE8, BRONZE = 0xFFD99A5B, ROW_A = 0x30F4F9FF, ROW_ME = 0x40F4C76B,
            READY = 0xFF70DB9B, LOCKED = 0xFF5D6A82;
    /** Height of the bag panel under a station. */
    private static final int BAG = 100;
    /** Stations that act on what you carry: they show your bag. */
    private static final List<String> BAG_KINDS = List.of("RepairService", "IdentificationService", "KitchenService", "AlchemyService",
            "ExpeditionForge", "ArcaneMenus", "SkillLampService", "AuctionController");

    private final ContainerScreen chest;
    private final ChestMenu menu;
    private final int chestSlots;
    private final boolean auctionBrowse, treeMenu, bagKind, merchant, auction;
    /** A talent tree's shape: its node slots, the connector slots drawn as paths, the paths, where each node sits. */
    record Graph(List<Integer> nodes, List<Integer> connectors, List<TalentGraph.Edge> edges,
                 java.util.function.BiFunction<Integer, Integer, net.aerborne.client.protocol.MenuLayout.Box> box,
                 java.util.function.IntUnaryOperator lightsAt, String label) { }
    private static final Graph SKILL_TREE = new Graph(TalentGraph.NODES, TalentGraph.CONNECTORS, TalentGraph.EDGES,
            TalentGraph::node, from -> from < 4 ? 2 : 1, "Branches");
    private static final Graph ISLAND_TREE = new Graph(IslandTalentGraph.NODES, IslandTalentGraph.CONNECTORS, IslandTalentGraph.EDGES,
            IslandTalentGraph::node, IslandTalentGraph::lightsAt, "");
    private final Graph graph;
    private boolean treeMode, tableMode, bag;
    private float scale = 1;
    private int left, top, page, bodyHeight;
    private MenuLayout layout;
    private EditBox search;
    private String query = "";
    private boolean refresh;
    private List<ItemStack> snapshot = List.of();
    private final List<Entry> content = new ArrayList<>(), tools = new ArrayList<>(), navigation = new ArrayList<>();
    /** Input slots and the labels the server put beside them; once a slot is a socket it stays one while open. */
    private final Set<Integer> sockets = new HashSet<>(), header = new HashSet<>();
    private final java.util.Map<Integer, ItemStack> socketLabels = new java.util.HashMap<>();
    private boolean socketsKnown;
    private record Entry(Slot slot, ItemStack item) { }

    NativeMenuScreen(ContainerScreen chest, String kind) {
        super(chest.getTitle());
        this.chest = chest; menu = chest.getMenu(); chestSlots = menu.getRowCount() * 9;
        auctionBrowse = MenuLayout.auctionBrowse(kind, title.getString(), chestSlots);
        graph = TalentGraph.matches(kind, title.getString(), chestSlots) ? SKILL_TREE
                : IslandTalentGraph.matches(kind, title.getString(), chestSlots) ? ISLAND_TREE : null;
        treeMenu = graph != null;
        bagKind = BAG_KINDS.stream().anyMatch(kind::contains);
        merchant = kind.contains("NpcManager");
        auction = kind.contains("AuctionController");
    }

    /** A merchant page that buys from you: its goods offer a sale. */
    private boolean selling() {
        for (int i = 0; i < chestSlots; i++)
            if (lore(menu.slots.get(i).getItem()).stream().anyMatch(l -> PRICE.matcher(l).matches())) return true;
        return false;
    }

    @Override protected void init() {
        boolean searchFocused = search != null && search.isFocused();
        int cursor = search == null ? query.length() : search.getCursorPosition();
        collect();
        bag = !treeMode && (bagKind || !sockets.isEmpty() || merchant && selling());
        int bagHeight = bag ? BAG : 0;
        int realWidth = minecraft.getWindow().getGuiScaledWidth(), realHeight = minecraft.getWindow().getGuiScaledHeight();
        // Native GUI pixels at ordinary sizes; shrink only below the minimum usable layout.
        scale = Math.min(1F, Math.min((realWidth - 8) / (treeMenu ? 540F : 400F), (realHeight - 8) / ((treeMenu ? 396F : 300F) + bagHeight)));
        width = Math.round(realWidth / scale); height = Math.round(realHeight / scale);
        layout = MenuLayout.of(Math.min(640, width - 8), Math.min(420, height - 8 - bagHeight), tools.size(), navigation.size(), content.size(), tableMode);
        bodyHeight = layout.height() + bagHeight;
        page = layout.clampPage(page);
        left = (width - layout.width()) / 2; top = (height - bodyHeight) / 2;
        search = new EditBox(font, left + layout.width() - 140, top + 6, 120, 10, Component.literal("Filter this page")) {
            @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, isFocused() ? Ui.SEARCH_FOCUSED : Ui.SEARCH, getX() - 18, getY() - 4, getWidth() + 22, 18);
                super.extractWidgetRenderState(g, mx, my, delta);
            }
        };
        search.setBordered(false);
        search.setTextColor(Ui.TEXT);
        search.setMaxLength(80);
        search.setHint(Component.literal("Filter this page…").withColor(Ui.MUTED));
        search.setValue(query); search.setCursorPosition(Math.min(cursor, query.length()));
        search.setResponder(value -> { query = value; page = 0; refresh = true; });
        if (!treeMode) addRenderableWidget(search);
        if (!treeMode && searchFocused) setFocused(search);
        for (int i = 0; i < tools.size(); i++) control(tools.get(i), layout.tool(i), toolLabel(tools.get(i)), false);
        for (int i = 0; i < navigation.size(); i++) control(navigation.get(i), layout.navigation(i), navLabel(navigation.get(i)), true);
        int offset = page * layout.perPage();
        if (treeMode) {
            for (int i = 0; i < graph.nodes().size(); i++) {
                var box = graph.box().apply(i, layout.width());
                var slot = menu.slots.get(graph.nodes().get(i));
                addRenderableWidget(new EntryButton(new Entry(slot, slot.getItem().copy()), left + box.x(), top + box.y(), box.width(), box.height(), null, false));
            }
        } else for (int i = 0; i < layout.perPage() && offset + i < content.size(); i++) {
            var box = layout.card(i);
            addRenderableWidget(new EntryButton(content.get(offset + i), left + box.x(), top + box.y(), box.width(), box.height(), null, false));
        }
        int bottom = top + layout.height() - 28;
        if (!treeMode && layout.pages() > 1) {
            arrow(left + 14, bottom, "page_left", "Previous view", () -> changePage(-1)).active = page > 0;
            arrow(left + 42, bottom, "page_right", "Next view", () -> changePage(1)).active = page + 1 < layout.pages();
        }
        addRenderableWidget(new AerborneMenuScreen.SpriteButton(left + layout.width() - 110, bottom, 96, 20,
                Component.literal("Classic view"), null, 0, false, b -> classic()));
        int mine = myRow();
        if (tableMode && mine >= 0) {
            // Jump to the view with your own row on it.
            addRenderableWidget(new AerborneMenuScreen.SpriteButton(left + layout.width() - 196, bottom, 80, 20,
                    Component.literal("Find me"), Ui.control("pin"), 14, false, b -> { page = layout.clampPage(mine / layout.perPage()); rebuildWidgets(); }));
        }
        snapshot = new ArrayList<>();
        for (int i = 0; i < chestSlots; i++) snapshot.add(menu.slots.get(i).getItem().copy());
        refresh = false;
    }

    private Button arrow(int x, int y, String icon, String label, Runnable action) {
        return addRenderableWidget(new AerborneMenuScreen.SpriteButton(x, y, 24, 20, Component.literal(label), Ui.control(icon), 14, true, b -> action.run()));
    }

    private void control(Entry entry, MenuLayout.Box box, String label, boolean nav) {
        addRenderableWidget(new EntryButton(entry, left + box.x(), top + box.y(), box.width(), box.height(), label, nav));
    }

    /** Your own row in a ranking's content, or -1. */
    private int myRow() {
        if (minecraft.player == null) return -1;
        String me = minecraft.player.getGameProfile().name();
        for (int i = 0; i < content.size(); i++) {
            Matcher m = RANK.matcher(content.get(i).item().getHoverName().getString());
            if (m.matches() && m.group(2).equalsIgnoreCase(me)) return i;
        }
        return -1;
    }

    private static List<String> lore(ItemStack item) {
        var lore = item.get(DataComponents.LORE);
        if (lore == null) return List.of();
        return lore.lines().stream().map(Component::getString).filter(s -> !s.isBlank()).toList();
    }

    /** A tab, filter or quantity: it says it is shown now or selected, or offers to change the view. */
    private static boolean chip(ItemStack item) {
        String name = item.getHoverName().getString();
        if (RANK.matcher(name).matches()) return false;
        // A quantity choice ("Buy 16"), not the purchase button that names a total.
        if (QUANTITY.matcher(name).matches() && lore(item).stream().noneMatch(l -> l.startsWith("Total") || l.startsWith("Payout")
                || l.startsWith("You receive") || l.startsWith("One bundle"))) return true;
        return lore(item).stream().anyMatch(l -> l.endsWith("shown now.") || l.equals("Showing now.") || l.equals("Selected")
                || l.equals("Click to view this ranking.") || l.equals("Click to change the filter.")
                || l.equals("Click to choose the next chapter.") || l.equals("Click to change quantity.")
                || l.equals("Focus this method. Your inputs stay in place."));
    }

    private static boolean active(ItemStack item) {
        // Station tabs mark the chosen one by naming it in green.
        int[] colour = {-1};
        item.getHoverName().visit((style, text) -> {
            if (text.isBlank()) return java.util.Optional.empty();
            colour[0] = style.getColor() == null ? -1 : style.getColor().getValue();
            return java.util.Optional.of(Boolean.TRUE);
        }, net.minecraft.network.chat.Style.EMPTY);
        if (colour[0] == 0x55FF55) return true;
        return lore(item).stream().anyMatch(l -> l.endsWith("shown now.") || l.equals("Showing now.") || l.startsWith("Selected"));
    }

    /** Separators the server draws with glass (＋, ╲, ╱): decoration, not a control. */
    private static boolean decoration(ItemStack item) {
        String id = item.getItem().builtInRegistryHolder().key().identifier().getPath();
        return id.endsWith("glass_pane") && item.getHoverName().getString().strip().length() <= 2;
    }

    private void findSockets() {
        if (socketsKnown) return;
        int fillers = 0; List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < chestSlots; i++) {
            ItemStack item = menu.slots.get(i).getItem();
            if (item.isEmpty()) empty.add(i); else if (AerborneMenus.filler(item)) fillers++;
        }
        // The screen opens before the server sends the contents; decide once the frame has arrived.
        if (fillers == 0 && empty.size() == chestSlots) return;
        socketsKnown = true;
        // A decorated menu leaves a slot empty only on purpose: it is an input. A few, never a whole storage grid.
        if (fillers == 0 || empty.isEmpty() || empty.size() > 4 || auctionBrowse || treeMenu) return;
        sockets.addAll(empty);
        for (int i : empty) {
            ItemStack beside = i % 9 > 0 ? menu.slots.get(i - 1).getItem() : ItemStack.EMPTY;
            if (!beside.isEmpty() && beside.getHoverName().getString().endsWith("Input")) socketLabels.put(i, beside.copy());
        }
    }

    private void collect() {
        content.clear(); tools.clear(); navigation.clear(); header.clear();
        treeMode = treeMenu && graph.nodes().stream().allMatch(i -> !menu.slots.get(i).getItem().isEmpty());
        findSockets();
        String filter = query.toLowerCase(Locale.ROOT).strip();
        int ranked = 0, topRow = 0, body = 0;
        for (int i = 0; i < chestSlots; i++) {
            ItemStack item = menu.slots.get(i).getItem();
            if (RANK.matcher(item.getHoverName().getString()).matches()) ranked++;
            if (item.isEmpty() || AerborneMenus.filler(item) || decoration(item) || chip(item)) continue;
            if (i < 9) topRow++; else if (i < chestSlots - 9) body++;
        }
        tableMode = !treeMode && ranked >= 3;
        // A few items alone on the top row, above the real content, are the page's header: a sign, a balance, a standing.
        boolean hasHeader = !treeMode && !tableMode && !auctionBrowse && topRow >= 1 && topRow <= 3 && body >= 1;
        Set<Integer> labels = new HashSet<>();
        for (int s : sockets) if (socketLabels.containsKey(s)) labels.add(s - 1);
        for (int s : sockets) content.add(new Entry(menu.slots.get(s), menu.slots.get(s).getItem().copy()));
        for (int i = 0; i < chestSlots; i++) {
            if (sockets.contains(i) || labels.contains(i)) continue;
            Slot slot = menu.slots.get(i); ItemStack item = slot.getItem();
            if (item.isEmpty() || AerborneMenus.filler(item) || decoration(item)) continue;
            Entry entry = new Entry(slot, item.copy());
            String name = item.getHoverName().getString();
            if (treeMode) {
                if (graph.nodes().contains(i)) content.add(entry);
                else if (graph.connectors().contains(i) && (name.contains("Connected path") || name.contains("Connected paths"))) { /* drawn as edges */ }
                else if (i >= chestSlots - 9 && i != 47 && i != 51) navigation.add(entry);
                else tools.add(entry);
                continue;
            }
            boolean bottomRow = i >= chestSlots - 9;
            // Position is part of the contract: an auctioned arrow called Back is still a listing.
            if (auctionBrowse ? bottomRow && (AerborneMenus.navigation(item) != null || i == 46 || name.startsWith("←") || name.endsWith("→"))
                    : bottomRow) navigation.add(entry);
            else if (auctionBrowse && i >= 2 && i <= 6) tools.add(entry);
            else if (hasHeader && i < 9 && !chip(item)) { header.add(i); tools.add(entry); }
            else if (!auctionBrowse && (chip(item) || tableMode && !RANK.matcher(name).matches())) tools.add(entry);
            else if (filter.isEmpty() || matches(item, filter)) content.add(entry);
        }
    }

    private static boolean matches(ItemStack item, String query) {
        if (item.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) return true;
        return lore(item).stream().anyMatch(line -> line.toLowerCase(Locale.ROOT).contains(query));
    }

    private String toolLabel(Entry e) {
        if (treeMode && graph == SKILL_TREE) return switch (e.slot().index) { case 0 -> "Progress & points"; case 47 -> "Read the tree";
            case 51 -> "Adventurer tree"; default -> e.item().getHoverName().getString(); };
        if (auctionBrowse) return switch (e.slot().index) { case 2 -> "Live auctions"; case 3 -> "My auctions"; case 4 -> "Deliveries";
            case 5 -> "Sell held item"; case 6 -> "Player stores"; default -> e.item().getHoverName().getString(); };
        return e.item().getHoverName().getString();
    }

    private String navLabel(Entry e) {
        String name = e.item().getHoverName().getString(), lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("previous page")) return "Previous page";
        if (lower.contains("next page")) return "Next page";
        if (lower.startsWith("← back") || lower.equals("back")) return "Back";
        if (lower.contains("close")) return "Close";
        if (auctionBrowse && e.slot().index == 46) return "Auction help";
        return name;
    }

    private static Identifier navIcon(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.equals("close")) return Ui.control("close");
        if (lower.equals("back")) return Ui.control("back");
        if (lower.startsWith("previous") || lower.startsWith("←")) return Ui.control("page_left");
        if (lower.startsWith("next") || lower.endsWith("→")) return Ui.control("page_right");
        if (lower.contains("help")) return Ui.control("help");
        if (lower.startsWith("refresh")) return Ui.control("refresh");
        return null;
    }

    @Override public void tick() {
        super.tick();
        for (int i = 0; !refresh && i < chestSlots; i++)
            if (i >= snapshot.size() || !ItemStack.matches(snapshot.get(i), menu.slots.get(i).getItem())) refresh = true;
        if (refresh) rebuildWidgets();
    }

    private void changePage(int delta) {
        if (treeMode) return;
        int next = layout.clampPage(page + delta);
        if (next != page) { page = next; rebuildWidgets(); }
    }

    private boolean live() {
        return minecraft.player != null && minecraft.gameMode != null && minecraft.player.containerMenu == menu;
    }

    private void click(Entry entry, int button, boolean shift) {
        // Reject changed items from an old render, or inventories already replaced by the server.
        if (!live()) return;
        if (!ItemStack.matches(entry.item(), entry.slot().getItem())) { refresh = true; return; }
        slotClick(entry.slot().index, button, shift);
    }

    private void slotClick(int slot, int button, boolean shift) {
        if (!live()) return;
        minecraft.gameMode.handleContainerInput(menu.containerId, slot, button, shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP, minecraft.player);
    }

    // ---- The bag: your inventory under a station, three rows and the hotbar, as the server's own slots. ----

    private int bagX() { return left + 14; }
    private int bagY() { return top + layout.height() + 12; }

    /** The menu slot index of the bag cell under the cursor, or -1. */
    private int bagSlotAt(double mx, double my) {
        if (!bag) return -1;
        int x = (int) Math.floor(mx) - bagX(), y = (int) Math.floor(my) - bagY();
        if (x < 0 || x >= 9 * 18) return -1;
        int column = x / 18;
        if (y >= 0 && y < 3 * 18) return chestSlots + y / 18 * 9 + column;
        if (y >= 3 * 18 + 4 && y < 4 * 18 + 4) return chestSlots + 27 + column;
        return -1;
    }

    private void drawBag(GuiGraphicsExtractor g, int mx, int my) {
        int x0 = bagX(), y0 = bagY();
        Ui.inset(g, x0 - 6, y0 - 8, 9 * 18 + 12, 4 * 18 + 16);
        int hovered = bagSlotAt(mx, my), held = minecraft.player == null ? -1 : minecraft.player.getInventory().getSelectedSlot();
        for (int k = 0; k < 36; k++) {
            int x = x0 + k % 9 * 18, y = k < 27 ? y0 + k / 9 * 18 : y0 + 3 * 18 + 4;
            int index = chestSlots + k;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, index == hovered ? Ui.SLOT_HOVER : Ui.SLOT, x, y, 18, 18);
            ItemStack item = menu.slots.get(index).getItem();
            if (!item.isEmpty()) {
                String rarity = AerborneMenus.rarityName(item);
                if (rarity != null && ClientState.preferences.rarity) Ui.icon(g, Ui.rarity(rarity), x, y, 18);
                g.item(item, x + 1, y + 1);
                g.itemDecorations(font, item, x + 1, y + 1);
            }
            if (k >= 27 && k - 27 == held) g.outline(x, y, 18, 18, Ui.GOLD);
            if (AerborneMenus.locked(menu.slots.get(index))) AerborneMenus.lockBadge(g, x + 1, y + 1);
        }
        int tx = x0 + 9 * 18 + 16, tw = layout.width() - (tx - left) - 14;
        g.text(font, Ui.heading("Your bag"), tx, y0 - 4, Ui.GOLD, true);
        ItemStack inHand = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getInventory().getSelectedItem();
        List<String> help = new ArrayList<>();
        help.add("In hand: " + (inHand.isEmpty() ? "nothing" : inHand.getHoverName().getString()));
        if (!sockets.isEmpty()) { help.add("Click an item, then a socket above to place it."); help.add("Shift-click sends it straight in."); }
        else if (auction) { help.add("Click any item here to auction it."); help.add("Nothing leaves your bag until you confirm."); }
        else if (merchant) { help.add("Right-click goods to review a sale."); help.add("Matching items here are what you sell."); }
        else help.add("Stations work on the item in your hand.");
        if (!merchant) help.add("Keys 1–9 choose what you hold.");
        for (int i = 0; i < help.size(); i++)
            g.text(font, font.plainSubstrByWidth(help.get(i), tw), tx, y0 + 10 + i * 11, i == 0 ? Ui.TEXT : Ui.MUTED, false);
    }

    private void classic() { AerborneMenus.CLASSIC.put(chest, true); minecraft.gui.setScreen(chest); }
    @Override public void onClose() { if (minecraft.player != null) minecraft.player.closeContainer(); else super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
    private MouseButtonEvent canvas(MouseButtonEvent e) { return new MouseButtonEvent(e.x() / scale, e.y() / scale, e.buttonInfo()); }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        var c = canvas(e);
        int bagSlot = bagSlotAt(c.x(), c.y());
        if (bagSlot >= 0 && (c.button() == 0 || c.button() == 1)) {
            if (!AerborneMenus.locked(menu.slots.get(bagSlot))) slotClick(bagSlot, c.button(), c.hasShiftDown());
            return true;
        }
        return super.mouseClicked(c, twice);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) { return super.mouseReleased(canvas(e)); }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return super.mouseDragged(canvas(e), dx / scale, dy / scale); }
    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        mx /= scale; my /= scale;
        if (mx >= left && mx < left + layout.width() && my >= top + layout.bodyTop() && my < top + layout.bodyBottom()) {
            changePage(dy > 0 ? -1 : dy < 0 ? 1 : 0); return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if (search.isFocused() && e.key() != GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(e);
        if (e.key() == GLFW.GLFW_KEY_PAGE_DOWN) { changePage(1); return true; }
        if (e.key() == GLFW.GLFW_KEY_PAGE_UP) { changePage(-1); return true; }
        if (bag && minecraft.player != null && e.key() >= GLFW.GLFW_KEY_1 && e.key() <= GLFW.GLFW_KEY_9) {
            // The server reads the held item for station work; the game sends the change on its next tick.
            minecraft.player.getInventory().setSelectedSlot(e.key() - GLFW.GLFW_KEY_1);
            return true;
        }
        if (minecraft.options.keyInventory.matches(e)) { onClose(); return true; }
        return super.keyPressed(e);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.pose().pushMatrix(); g.pose().scale(scale, scale);
        int mx = Math.round(mouseX / scale), my = Math.round(mouseY / scale);
        Ui.window(g, left, top, layout.width(), bodyHeight);
        Ui.windowTitle(g, font, title.getString(), left, top, layout.width(), treeMode ? 0 : 160);
        if (treeMode) drawTree(g);
        else {
            int shown = content.size() - sockets.size();
            String count = shown + (tableMode ? " ranked" : auctionBrowse ? " on this server page" : shown == 1 ? " entry" : " entries")
                    + (query.isBlank() ? "" : " match your filter");
            g.text(font, sockets.isEmpty() ? count : "Workbench", left + 14, top + layout.bodyTop() - 11, Ui.MUTED, false);
        }
        if (content.isEmpty() && !treeMode) g.text(font, query.isBlank() ? "Nothing here yet." : "Nothing on this page matches.",
                left + 18, top + layout.bodyTop() + 14, Ui.MUTED, false);
        if (!treeMode && layout.pages() > 1)
            g.text(font, "View " + (page + 1) + " / " + layout.pages(), left + 72, top + layout.height() - 22, Ui.MUTED, false);
        if (treeMode) g.text(font, "Hover a talent for its requirements, click to review it", left + 14, top + layout.height() - 22, Ui.MUTED, false);
        if (bag) drawBag(g, mx, my);
        super.extractRenderState(g, mx, my, delta);
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            // The item on the cursor, as the vanilla screen draws it.
            g.item(carried, mx - 8, my - 8);
            g.itemDecorations(font, carried, mx - 8, my - 8);
        } else {
            // Every line of the item's own tooltip, including confirmation terms and click instructions, in the Aerborne frame.
            ItemStack hover = ItemStack.EMPTY;
            int bagSlot = bagSlotAt(mx, my);
            if (bagSlot >= 0) hover = menu.slots.get(bagSlot).getItem();
            else for (var child : children()) if (child instanceof EntryButton entry && entry.isHovered()) {
                hover = entry.entry.item().isEmpty() ? socketLabels.getOrDefault(entry.entry.slot().index, ItemStack.EMPTY) : entry.entry.item();
                break;
            }
            if (!hover.isEmpty()) g.setTooltipForNextFrame(font, Screen.getTooltipFromItem(minecraft, hover), hover.getTooltipImage(), mx, my, TOOLTIP);
        }
        g.pose().popMatrix();
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) { g.fill(0, 0, width, height, 0x90000000); }

    private Component trimmed(Component text, int pixels) {
        if (font.width(text) <= pixels) return text;
        String s = text.getString();
        while (!s.isEmpty() && font.width(Component.literal(s + "…").withStyle(text.getStyle())) > pixels) s = s.substring(0, s.length() - 1);
        return Component.literal(s + "…").withStyle(text.getStyle());
    }

    /** Lines for a card: prices, costs and timers first, then the description. */
    private static List<Component> details(Entry entry) {
        var lore = entry.item().get(DataComponents.LORE);
        if (lore == null) return List.of();
        List<Component> lines = lore.lines().stream().filter(line -> !line.getString().isBlank())
                .filter(line -> !line.getString().startsWith("Click to") && !line.getString().startsWith("---")).toList();
        var key = new ArrayList<Component>();
        for (Component line : lines) if (KEY_LINE.matcher(line.getString().strip()).matches()) key.add(line);
        key.sort(java.util.Comparator.comparingInt(line -> {
            String text = line.getString().strip();
            for (int i = 0; i < KEY_ORDER.size(); i++) if (text.startsWith(KEY_ORDER.get(i))) return i;
            return KEY_ORDER.size();
        }));
        if (key.size() >= 2) return key;
        var result = new ArrayList<>(key);
        for (Component line : lines) if (!key.contains(line)) result.add(line);
        return result;
    }

    /** How far along a card's progress line is, 0 to 1, or -1 when it has none. */
    private static float progress(ItemStack item) {
        for (String line : lore(item)) {
            Matcher m = PROGRESS.matcher(line.strip());
            if (!m.matches()) continue;
            try {
                long have = Long.parseLong(m.group(1).replace(",", "")), of = Long.parseLong(m.group(2).replace(",", ""));
                if (of > 0) return Math.min(1F, have / (float) of);
            } catch (NumberFormatException ignored) { }
        }
        return -1;
    }

    private static int rank(ItemStack item) {
        for (String line : lore(item)) {
            var match = Pattern.compile("^Rank (\\d+)/\\d+$").matcher(line);
            if (match.matches()) return Integer.parseInt(match.group(1));
        }
        // Island talents carry their rank in the name: "Prospecting 1/3".
        Matcher named = Pattern.compile("(\\d+)/\\d+$").matcher(item.getHoverName().getString().strip());
        return named.find() ? Integer.parseInt(named.group(1)) : 0;
    }

    private static boolean ready(ItemStack item) { return lore(item).stream().anyMatch(line -> line.equals("Click to review this rank.")); }

    private void drawTree(GuiGraphicsExtractor g) {
        if (!graph.label().isEmpty()) g.text(font, graph.label(), left + 14, top + 82, Ui.MUTED, false);
        for (var edge : graph.edges()) {
            var from = graph.box().apply(edge.from(), layout.width()); var to = graph.box().apply(edge.to(), layout.width());
            int x1 = left + from.x() + from.width() / 2, y1 = top + from.y() + from.height();
            int x2 = left + to.x() + to.width() / 2, y2 = top + to.y();
            int middle = (y1 + y2) / 2;
            boolean lit = rank(menu.slots.get(graph.nodes().get(edge.from())).getItem()) >= graph.lightsAt().applyAsInt(edge.from());
            int color = lit ? Ui.GOLD : 0xFF3A4660, glow = lit ? 0x60F4C76B : 0;
            // A two-pixel path with a soft glow once it is open.
            if (glow != 0) {
                g.fill(x1 - 2, y1, x1 + 4, middle, glow);
                g.fill(Math.min(x1, x2) - 2, middle - 2, Math.max(x1, x2) + 4, middle + 4, glow);
                g.fill(x2 - 2, middle, x2 + 4, y2, glow);
            }
            g.fill(x1, y1, x1 + 2, middle, color);
            g.fill(Math.min(x1, x2), middle, Math.max(x1, x2) + 2, middle + 2, color);
            g.fill(x2, middle, x2 + 2, y2, color);
        }
        int y = top + 312;
        legend(g, left + 14, y, READY, "Ready to learn");
        legend(g, left + 124, y, Ui.GOLD, "Learned");
        legend(g, left + 204, y, LOCKED, "Locked");
    }

    private void legend(GuiGraphicsExtractor g, int x, int y, int colour, String label) {
        g.fill(x, y + 1, x + 7, y + 8, colour);
        g.text(font, label, x + 11, y, Ui.MUTED, false);
    }

    private void badge(GuiGraphicsExtractor g, ItemStack item, int x, int y, boolean listing) {
        Ui.icon(g, Ui.SLOT, x, y, 36);
        if (item.isEmpty()) return;
        var rarity = AerborneMenus.rarityName(item);
        if (rarity != null) Ui.icon(g, Ui.rarity(rarity), x, y, 36);
        // Listed goods always show their actual item, even when named Island or Shop.
        var icon = listing || rarity != null ? null : AerborneMenus.menuIcon(item.getHoverName().getString());
        if (icon != null) Ui.icon(g, icon, x + 2, y + 2, 32);
        else { g.pose().pushMatrix(); g.pose().translate(x + 2, y + 2); g.pose().scale(2F, 2F); g.item(item, 0, 0); g.pose().popMatrix(); }
        if (item.getCount() > 1) {
            String count = String.valueOf(item.getCount());
            g.text(font, count, x + 33 - font.width(count), y + 25, Ui.TEXT, true);
        }
    }

    private final class EntryButton extends Button {
        private final Entry entry;
        private final String compactLabel;
        private final boolean nav;
        EntryButton(Entry entry, int x, int y, int w, int h, String compactLabel, boolean nav) {
            super(x, y, w, h, entry.item().getHoverName(), b -> {}, DEFAULT_NARRATION);
            this.entry = entry; this.compactLabel = compactLabel; this.nav = nav;
        }
        @Override public void onPress(InputWithModifiers input) { click(entry, 0, input.hasShiftDown()); }
        @Override protected boolean isValidClickButton(net.minecraft.client.input.MouseButtonInfo button) { return button.button() == 0 || button.button() == 1; }
        @Override public void onClick(MouseButtonEvent e, boolean twice) { click(entry, e.button(), e.hasShiftDown()); }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
            boolean focus = isHoveredOrFocused();
            if (compactLabel != null) { if (header.contains(entry.slot().index)) info(g, focus); else control(g, focus); return; }
            if (treeMode) { node(g, focus); return; }
            if (tableMode) { row(g, focus); return; }
            if (sockets.contains(entry.slot().index)) { socket(g, focus); return; }
            String name = entry.item().getHoverName().getString();
            boolean confirm = name.startsWith("Confirm");
            Ui.inset(g, getX(), getY(), getWidth(), getHeight());
            if (focus || confirm) g.outline(getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, confirm ? READY : Ui.GOLD);
            badge(g, entry.item(), getX() + 6, getY() + 6, true);
            int tx = getX() + 48, tw = getWidth() - 54;
            g.text(font, trimmed(entry.item().getHoverName(), tw), tx, getY() + 7, Ui.TEXT, true);
            var lines = details(entry);
            for (int i = 0; i < Math.min(2, lines.size()); i++) g.text(font, trimmed(lines.get(i), tw), tx, getY() + 20 + i * 11, Ui.MUTED, false);
            float progress = progress(entry.item());
            if (progress >= 0) {
                int bx = tx, bw = tw, by = getY() + getHeight() - 5;
                g.fill(bx, by, bx + bw, by + 2, 0xFF0D1A2B);
                g.fill(bx, by, bx + Math.round(bw * progress), by + 2, progress >= 1 ? READY : Ui.GOLD);
            }
        }

        /** An input slot: a deep socket with the server's label beside it. */
        private void socket(GuiGraphicsExtractor g, boolean focus) {
            ItemStack item = entry.slot().getItem(), label = socketLabels.get(entry.slot().index);
            Ui.inset(g, getX(), getY(), getWidth(), getHeight());
            g.outline(getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, focus ? Ui.GOLD_LIGHT : item.isEmpty() ? 0xFF3A4660 : Ui.GOLD);
            Ui.icon(g, focus ? Ui.SLOT_HOVER : Ui.SLOT, getX() + 6, getY() + 6, 36);
            if (!item.isEmpty()) {
                g.pose().pushMatrix(); g.pose().translate(getX() + 8, getY() + 8); g.pose().scale(2F, 2F); g.item(item, 0, 0); g.pose().popMatrix();
            }
            int tx = getX() + 48, tw = getWidth() - 54;
            Component heading = label != null ? label.getHoverName() : Component.literal("Input");
            g.text(font, trimmed(heading, tw), tx, getY() + 7, Ui.GOLD, true);
            String line1 = item.isEmpty() ? "Empty" : item.getHoverName().getString();
            String line2 = menu.getCarried().isEmpty() ? (item.isEmpty() ? "Choose an item from your bag" : "Click to take it back")
                    : "Click to place " + menu.getCarried().getHoverName().getString();
            g.text(font, font.plainSubstrByWidth(line1, tw), tx, getY() + 20, item.isEmpty() ? Ui.MUTED : Ui.TEXT, false);
            g.text(font, font.plainSubstrByWidth(line2, tw), tx, getY() + 31, Ui.MUTED, false);
        }

        /** A header: the item, its name in gold and its most useful line, on a recessed plate. */
        private void info(GuiGraphicsExtractor g, boolean focus) {
            Ui.inset(g, getX(), getY(), getWidth(), getHeight());
            if (focus) g.outline(getX(), getY(), getWidth(), getHeight(), Ui.GOLD_LIGHT);
            g.item(entry.item(), getX() + 2, getY() + 2);
            var lines = details(entry);
            Component name = entry.item().getHoverName();
            int room = getWidth() - 24;
            if (!lines.isEmpty() && font.width(name) + 12 < room) {
                g.text(font, name, getX() + 20, getY() + 6, Ui.GOLD, true);
                int used = font.width(name) + 8;
                g.text(font, trimmed(lines.getFirst(), room - used), getX() + 20 + used, getY() + 6, Ui.MUTED, false);
            } else g.text(font, trimmed(name, room), getX() + 20, getY() + 6, Ui.GOLD, true);
        }

        /** A chip, or a footer button with its icon. The chip that is "shown now" is lit in gold. */
        private void control(GuiGraphicsExtractor g, boolean focus) {
            boolean on = !nav && active(entry.item());
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Ui.BUTTON.get(true, focus || on), getX(), getY(), getWidth(), getHeight());
            if (on) g.outline(getX(), getY(), getWidth(), getHeight(), Ui.GOLD);
            Identifier icon = nav ? navIcon(compactLabel) : null;
            // Footer items with no icon of ours (a page counter, a refresh clock) show their own item.
            boolean ownItem = nav && icon == null;
            int iconWidth = icon != null || ownItem ? 16 : 0;
            var label = trimmed(Component.literal(compactLabel.replace("←", "").replace("→", "").strip()), getWidth() - 10 - iconWidth);
            int total = iconWidth + font.width(label), x = getX() + (getWidth() - total) / 2;
            if (icon != null) Ui.icon(g, icon, x, getY() + 3, 14);
            else if (ownItem) {
                g.pose().pushMatrix(); g.pose().translate(x, getY() + 3); g.pose().scale(0.875F, 0.875F); g.item(entry.item(), 0, 0); g.pose().popMatrix();
            }
            g.text(font, label, x + iconWidth, getY() + 6, on ? Ui.GOLD : focus ? Ui.GOLD_LIGHT : Ui.TEXT, true);
        }

        /** A ranking row: medal-coloured place, the board's icon, the name, and the score at the right. */
        private void row(GuiGraphicsExtractor g, boolean focus) {
            Matcher match = RANK.matcher(entry.item().getHoverName().getString());
            String place = match.matches() ? match.group(1) : "?", name = match.matches() ? match.group(2) : entry.item().getHoverName().getString();
            boolean me = minecraft.player != null && name.equalsIgnoreCase(minecraft.player.getGameProfile().name());
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), me ? ROW_ME : (Integer.parseInt("0" + place.replaceAll("\\D", "")) % 2 == 0 ? ROW_A : 0x18F4F9FF));
            if (focus) g.outline(getX(), getY(), getWidth(), getHeight(), Ui.GOLD);
            int medal = switch (place) { case "1" -> Ui.GOLD; case "2" -> SILVER; case "3" -> BRONZE; default -> Ui.MUTED; };
            g.text(font, Ui.heading("#" + place), getX() + 5, getY() + 5, medal, true);
            g.item(entry.item(), getX() + 34, getY() + 3);
            List<String> lines = lore(entry.item());
            String score = lines.isEmpty() ? "" : lines.getFirst();
            int scoreWidth = font.width(score);
            g.text(font, font.plainSubstrByWidth(name, getWidth() - 62 - scoreWidth - 8), getX() + 54, getY() + 7, me ? Ui.GOLD_LIGHT : Ui.TEXT, false);
            g.text(font, score, getX() + getWidth() - 6 - scoreWidth, getY() + 7, Ui.GOLD, false);
        }

        /** A talent: its item in a badge, its name, and its state (ready, learned with its ranks, or locked). */
        private void node(GuiGraphicsExtractor g, boolean focus) {
            boolean isReady = ready(entry.item());
            int ranks = rank(entry.item());
            int colour = isReady ? READY : ranks > 0 ? Ui.GOLD : LOCKED;
            Ui.inset(g, getX(), getY(), getWidth(), getHeight());
            g.outline(getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, focus ? Ui.GOLD_LIGHT : colour);
            badge(g, entry.item(), getX() + 6, getY() + 8, true);
            String name = entry.item().getHoverName().getString().replaceAll("\\s+\\d+/\\d+$", "");
            g.text(font, trimmed(Component.literal(name), getWidth() - 54), getX() + 48, getY() + 10, isReady || ranks > 0 ? colour : Ui.TEXT, true);
            Matcher max = Pattern.compile("(\\d+)/(\\d+)$").matcher(entry.item().getHoverName().getString());
            int of = max.find() ? Integer.parseInt(max.group(2)) : 1;
            // One pip per rank: filled for learned ranks.
            for (int i = 0; i < Math.min(of, 5); i++) {
                int px = getX() + 48 + i * 10;
                g.fill(px, getY() + 26, px + 7, getY() + 33, 0xFF0D1A2B);
                if (i < ranks) g.fill(px + 1, getY() + 27, px + 6, getY() + 32, Ui.GOLD);
                else g.outline(px, getY() + 26, 7, 7, colour);
            }
            String state = isReady ? "Ready" : ranks > 0 ? "Learned" : "Locked";
            g.text(font, state, getX() + getWidth() - 6 - font.width(state), getY() + 26, colour, false);
        }
    }
}
