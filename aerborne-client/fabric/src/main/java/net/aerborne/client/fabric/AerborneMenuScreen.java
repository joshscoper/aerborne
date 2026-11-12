package net.aerborne.client.fabric;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * Presentation-only entry point. Every action still passes through the server command system.
 *
 * <p>The menu is a gold-framed panel under a winged header ribbon. Each destination is a tile with its own icon;
 * its description shows as a tooltip in the Aerborne frame. The art comes from {@code tools/slice_assets.py}.
 */
public final class AerborneMenuScreen extends Screen {
    private record Destination(String title, String detail, String command, String icon) {}
    private static final List<Destination> DESTINATIONS = List.of(
            new Destination("Character Overview", "Vitals, gear and durability", "@character", "equipment"),
            new Destination("Personal Waypoints", "Save favorite places and track their distance", "@waypoints", "your_island"),
            new Destination("Appearance & HUD", "Themes, HUD size, position and visibility", "@appearance", "player_settings"),
            new Destination("Your Island", "Home, co-op and island development", "island", "your_island"),
            new Destination("Equipment", "Armor, weapons, jewelry and trinkets", "equipment", "equipment"),
            new Destination("Skills & Talents", "Explore your skill progression", "skills", "skills_talents"),
            new Destination("Adventurer Tree", "Shape your character's journey", "adventurer", "adventurer_tree"),
            new Destination("Quest Log", "Read your story quests and objectives", "journal", "quest_log"),
            new Destination("Island Journal", "Discover your island's next steps", "island journal", "island_journal"),
            new Destination("Auction House", "Browse player listings", "ah", "auction_house"),
            new Destination("Shops", "Browse NPC merchants", "shops", "shops"),
            new Destination("Achievements", "Explore your milestones", "achievements", "achievements"),
            new Destination("Player Settings", "Privacy, chat and personal displays", "settings", "player_settings"),
            new Destination("Tutorial & Help", "Find your next step", "quickstart", "tutorial_help"),
            new Destination("All Menus", "Open the complete server menu", "menu", "all_menus"),
            new Destination("Friends & Party", "Your party, friends and requests in one place", "social", "player_settings")
    );

    private static final Component TAGLINE = Component.literal("Small beginnings. Endless skies.");
    private static final Identifier PANEL = sprite("menu/panel");
    private static final WidgetSprites BUTTON = Ui.BUTTON;
    /** Tooltips drawn with {@code tooltip/menu_background}: the dark box with the gold line. */
    private static final Identifier TOOLTIP_STYLE = sprite("menu");
    /** The gold title plaque (tools/make_title_plaque.py), drawn one art pixel per GUI pixel so it stays crisp. */
    private static final Identifier HEADER = sprite("textures/gui/brand/title_plaque.png");
    private static final int HEADER_TEXTURE_WIDTH = 139, HEADER_TEXTURE_HEIGHT = 41;
    private static final int GOLD = 0xFFF4C76B, CLOUD = 0xFFF4F9FF;

    /** A tile is tall enough for its 32 pixel icon; plain buttons keep Minecraft's 20. */
    private static final int TILE_HEIGHT = 40, TILE_GAP = 6, MARGIN = 14, BUTTON_HEIGHT = 20;
    /** How much of the header stands above the panel; the rest overlaps its top edge. */
    private static final int HEADER_RISE = 20;

    private final BooleanSupplier available;
    private int page;
    private int section;
    private int pageCount;
    private static final String[] SECTIONS = {"Home", "Character", "Adventure", "Island", "Client"};
    private int top;
    private int panelLeft, panelTop, panelWidth, panelHeight;
    private int headerWidth, headerHeight;

    public AerborneMenuScreen(BooleanSupplier available) {
        super(Component.literal("Aerborne"));
        this.available = available;
    }

    private static Identifier sprite(String path) { return Identifier.fromNamespaceAndPath("aerborne_client", path); }

    private List<Destination> destinations() {
        int[] indexes = switch (section) {
            case 1 -> new int[] {0, 4, 5, 6, 11, 15};
            case 2 -> new int[] {7, 8, 12, 13, 14};
            case 3 -> new int[] {3, 9, 10, 1};
            case 4 -> new int[] {2, 1, 12, 14};
            default -> new int[] {0, 3, 7, 5, 15, 9};
        };
        return java.util.Arrays.stream(indexes).mapToObj(DESTINATIONS::get).toList();
    }

    @Override protected void init() {
        int columns = width >= 360 ? 2 : 1;
        int rows = Math.max(1, Math.min(3, (height - 150) / 60));
        int perPage = columns * rows;
        var destinations = destinations();
        pageCount = Math.max(1, (destinations.size() + perPage - 1) / perPage);
        page = Math.min(page, pageCount - 1);
        panelWidth = Math.min(560, width - 20);
        headerWidth = HEADER_TEXTURE_WIDTH;
        headerHeight = HEADER_TEXTURE_HEIGHT;
        panelHeight = 98 + rows * 60 + 34;
        panelTop = Math.max(14, (height - panelHeight) / 2);
        top = panelTop + 8;
        panelLeft = (width - panelWidth) / 2;
        int left = panelLeft + 16, inner = panelWidth - 32;
        int tabWidth = (inner - 16) / 5;
        for (int i = 0; i < SECTIONS.length; i++) {
            int nextSection = i;
            var tab = addRenderableWidget(new SpriteButton(left + i * (tabWidth + 4), panelTop + 66, tabWidth, 20,
                    Component.literal(SECTIONS[i]), null, 0, false, b -> { section = nextSection; page = 0; rebuildWidgets(); }));
            tab.selected = section == i;
            tab.setTooltip(tooltip(SECTIONS[i]));
        }
        int tileWidth = (inner - (columns - 1) * 10) / columns;
        for (int i = 0; i < perPage && page * perPage + i < destinations.size(); i++) {
            Destination destination = destinations.get(page * perPage + i);
            CardButton tile = addRenderableWidget(new CardButton(
                    left + (i % columns) * (tileWidth + 10), panelTop + 98 + (i / columns) * 60,
                    tileWidth, destination, button -> open(destination)));
            tile.setTooltip(tooltip(destination.detail()));
        }
        int footer = panelTop + panelHeight - 30;
        if (pageCount > 1) {
            SpriteButton back = addRenderableWidget(new SpriteButton(left, footer, 32, BUTTON_HEIGHT,
                    Component.literal("Previous page"), sprite("icon/nav/left"), 16, true, button -> { page--; rebuildWidgets(); }));
            back.active = page > 0;
            back.setTooltip(tooltip("Previous page"));
            SpriteButton next = addRenderableWidget(new SpriteButton(left + inner - 32, footer, 32, BUTTON_HEIGHT,
                    Component.literal("Next page"), sprite("icon/nav/right"), 16, true, button -> { page++; rebuildWidgets(); }));
            next.active = page + 1 < pageCount;
            next.setTooltip(tooltip("Next page"));
        }
        int closeWidth = Math.min(120, inner - (pageCount > 1 ? 76 : 0));
        addRenderableWidget(new SpriteButton(width / 2 - closeWidth / 2, footer, closeWidth, BUTTON_HEIGHT,
                Component.literal("Back to game"), null, 0, false, button -> onClose()));
    }

    private static Tooltip tooltip(String text) {
        return Tooltip.create(Component.literal(text), Optional.empty(), TOOLTIP_STYLE);
    }

    private void open(Destination destination) {
        if (destination.command().equals("@appearance")) { minecraft.gui.setScreen(new AppearanceScreen(this)); return; }
        if (destination.command().equals("@character")) { minecraft.gui.setScreen(new CharacterScreen(this)); return; }
        if (destination.command().equals("@waypoints")) { minecraft.gui.setScreen(new WaypointScreen(this)); return; }
        if (!available.getAsBoolean() || minecraft.getConnection() == null) { onClose(); return; }
        onClose();
        minecraft.getConnection().sendCommand("nerdsmp:" + destination.command());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // The panel and header go down first; the buttons are drawn over them by the screen itself.
        Ui.inset(graphics, panelLeft, panelTop, panelWidth, panelHeight);
        int headerLeft = (width - headerWidth) / 2;
        // The plaque carries the name in its own lettering; it rises a little above the panel's top edge.
        graphics.blit(RenderPipelines.GUI_TEXTURED, HEADER, headerLeft, panelTop - 12, 0F, 0F, headerWidth, headerHeight,
                HEADER_TEXTURE_WIDTH, HEADER_TEXTURE_HEIGHT, HEADER_TEXTURE_WIDTH, HEADER_TEXTURE_HEIGHT);
        graphics.centeredText(font, TAGLINE, width / 2, panelTop + headerHeight - 6, 0xFFE8D9B0);
        if (pageCount > 1) graphics.text(font, (page + 1) + " / " + pageCount,
                panelLeft + 58, panelTop + panelHeight - 24, Ui.MUTED, false);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override public boolean isPauseScreen() { return false; }

    /**
     * A button in the Aerborne frame, with an optional icon. A tile shows its icon at the left and its name beside
     * it; an icon-only button shows the icon alone and keeps its name for narration and its tooltip.
     */
    public static final class SpriteButton extends Button {
        private final Identifier icon;
        private final int iconSize;
        private final boolean iconOnly;
        boolean selected;

        public SpriteButton(int x, int y, int width, int height, Component message, Identifier icon, int iconSize, boolean iconOnly, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.icon = icon;
            this.iconSize = iconSize;
            this.iconOnly = iconOnly;
        }

        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            boolean highlighted = active && (isHoveredOrFocused() || selected);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BUTTON.get(active, highlighted),
                    getX(), getY(), getWidth(), getHeight(), ARGB.white(alpha));
            int iconTop = getY() + (getHeight() - iconSize) / 2;
            // A greyed button's icon fades with it.
            int tint = ARGB.white(active ? alpha : alpha * 0.4F);
            if (iconOnly) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, getX() + (getWidth() - iconSize) / 2, iconTop, iconSize, iconSize, tint);
                return;
            }
            ActiveTextCollector text = graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE);
            if (icon == null) {
                var font = net.minecraft.client.Minecraft.getInstance().font;
                String label = Ui.fit(font, getMessage().getString(), getWidth() - 12);
                graphics.text(font, label, getX() + (getWidth() - font.width(label)) / 2, getY() + (getHeight() - 8) / 2,
                        !active ? Ui.MUTED : highlighted ? 0xFF17243B : Ui.TEXT, !highlighted);
                return;
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, getX() + 6, iconTop, iconSize, iconSize, tint);
            text.accept(TextAlignment.LEFT, getX() + 6 + iconSize + 6, getY() + (getHeight() - 8) / 2, getMessage());
        }
    }

    /** Large quiet targets: an icon, a title and a useful description, with gold reserved for focus. */
    private static final class CardButton extends Button {
        private final Destination destination;
        CardButton(int x, int y, int width, Destination destination, OnPress action) {
            super(x, y, width, 52, Component.literal(destination.title()), action, DEFAULT_NARRATION);
            this.destination = destination;
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
            var font = net.minecraft.client.Minecraft.getInstance().font;
            boolean focus = isHoveredOrFocused();
            Ui.inset(g, getX(), getY(), getWidth(), getHeight());
            if (focus) g.outline(getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, Ui.GOLD);
            // The destination's hand-drawn icon in a framed slot, at twice its size.
            Ui.icon(g, Ui.SLOT, getX() + 8, getY() + 8, 36);
            var icon = AerborneMenus.menuIcon(destination.title());
            if (icon != null) Ui.icon(g, icon, getX() + 10, getY() + 10, 32);
            else g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("icon/menu/" + destination.icon()), getX() + 14, getY() + 14, 24, 24);
            int x = getX() + 52, textWidth = getWidth() - 60;
            Ui.headingFit(g, font, destination.title(), x, getY() + 11, textWidth, focus ? Ui.GOLD_LIGHT : Ui.GOLD);
            g.text(font, Ui.fit(font, destination.detail(), textWidth), x, getY() + 29, Ui.MUTED, false);
        }
    }
}
