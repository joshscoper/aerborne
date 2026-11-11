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
            new Destination("Character Overview", "Live vitals, equipment details and durability", "@character", "equipment"),
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
            new Destination("All Menus", "Open the complete server menu", "menu", "all_menus")
    );

    private static final Component TAGLINE = Component.literal("Small beginnings. Endless skies.");
    private static final Identifier PANEL = sprite("menu/panel");
    private static final WidgetSprites BUTTON = new WidgetSprites(
            sprite("menu/button"), sprite("menu/button_disabled"), sprite("menu/button_highlighted"));
    /** Tooltips drawn with {@code tooltip/menu_background}: the dark box with the gold line. */
    private static final Identifier TOOLTIP_STYLE = sprite("menu");
    private static final Identifier HEADER = sprite("textures/gui/brand/header.png");
    private static final int HEADER_TEXTURE_WIDTH = 960, HEADER_TEXTURE_HEIGHT = 144;
    private static final int GOLD = 0xFFF4C76B, CLOUD = 0xFFF4F9FF;

    /** A tile is tall enough for its 32 pixel icon; plain buttons keep Minecraft's 20. */
    private static final int TILE_HEIGHT = 40, TILE_GAP = 6, MARGIN = 14, BUTTON_HEIGHT = 20;
    /** How much of the header stands above the panel; the rest overlaps its top edge. */
    private static final int HEADER_RISE = 20;

    private final BooleanSupplier available;
    private int page;
    private int top;
    private int panelLeft, panelTop, panelWidth, panelHeight;
    private int headerWidth, headerHeight;

    public AerborneMenuScreen(BooleanSupplier available) {
        super(Component.literal("Aerborne"));
        this.available = available;
    }

    private static Identifier sprite(String path) { return Identifier.fromNamespaceAndPath("aerborne_client", path); }

    @Override protected void init() {
        int columns = width >= 600 ? 3 : width >= 280 ? 2 : 1;
        int rows = Math.max(1, Math.min(3, (height - 96) / (TILE_HEIGHT + TILE_GAP)));
        int perPage = columns * rows;
        int pages = (DESTINATIONS.size() + perPage - 1) / perPage;
        page = Math.min(page, pages - 1);

        panelWidth = Math.min(columns == 3 ? 540 : columns == 2 ? 360 : 220, width - 16);
        headerWidth = Math.min(240, panelWidth - 16);
        headerHeight = headerWidth * HEADER_TEXTURE_HEIGHT / HEADER_TEXTURE_WIDTH;
        int tilesTop = headerHeight - HEADER_RISE + 22;
        int tilesHeight = rows * (TILE_HEIGHT + TILE_GAP) - TILE_GAP;
        panelHeight = tilesTop + tilesHeight + 8 + BUTTON_HEIGHT + 12;
        top = Math.max(4, (height - (HEADER_RISE + panelHeight)) / 2);
        panelTop = top + HEADER_RISE;
        panelLeft = (width - panelWidth) / 2;

        int left = panelLeft + MARGIN;
        int inner = panelWidth - MARGIN * 2;
        int tileWidth = (inner - (columns - 1) * TILE_GAP) / columns;
        for (int i = 0; i < perPage && page * perPage + i < DESTINATIONS.size(); i++) {
            Destination destination = DESTINATIONS.get(page * perPage + i);
            SpriteButton tile = addRenderableWidget(new SpriteButton(
                    left + (i % columns) * (tileWidth + TILE_GAP), panelTop + tilesTop + (i / columns) * (TILE_HEIGHT + TILE_GAP),
                    tileWidth, TILE_HEIGHT, Component.literal(destination.title()),
                    sprite("icon/menu/" + destination.icon()), 32, false, button -> open(destination)));
            tile.setTooltip(tooltip(destination.detail()));
        }

        int footer = panelTop + tilesTop + tilesHeight + 8;
        if (pages > 1) {
            SpriteButton back = addRenderableWidget(new SpriteButton(left, footer, 32, BUTTON_HEIGHT,
                    Component.literal("Previous page"), sprite("icon/nav/left"), 16, true, button -> { page--; rebuildWidgets(); }));
            back.active = page > 0;
            back.setTooltip(tooltip("Previous page"));
            SpriteButton next = addRenderableWidget(new SpriteButton(left + inner - 32, footer, 32, BUTTON_HEIGHT,
                    Component.literal("Next page"), sprite("icon/nav/right"), 16, true, button -> { page++; rebuildWidgets(); }));
            next.active = page + 1 < pages;
            next.setTooltip(tooltip("Next page"));
        }
        int closeWidth = Math.min(120, inner - (pages > 1 ? 76 : 0));
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
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, panelLeft, panelTop, panelWidth, panelHeight);
        int headerLeft = (width - headerWidth) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, HEADER, headerLeft, top, 0F, 0F, headerWidth, headerHeight,
                HEADER_TEXTURE_WIDTH, HEADER_TEXTURE_HEIGHT, HEADER_TEXTURE_WIDTH, HEADER_TEXTURE_HEIGHT);
        // The ribbon's writing space is the lower two thirds of the art, between its top star and bottom rim.
        graphics.centeredText(font, title, width / 2, top + headerHeight * 57 / 100 - 4, ClientState.preferences.theme.accent);
        graphics.centeredText(font, TAGLINE, width / 2, top + headerHeight + 7, CLOUD);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override public boolean isPauseScreen() { return false; }

    /**
     * A button in the Aerborne frame, with an optional icon. A tile shows its icon at the left and its name beside
     * it; an icon-only button shows the icon alone and keeps its name for narration and its tooltip.
     */
    static final class SpriteButton extends Button {
        private final Identifier icon;
        private final int iconSize;
        private final boolean iconOnly;

        SpriteButton(int x, int y, int width, int height, Component message, Identifier icon, int iconSize, boolean iconOnly, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.icon = icon;
            this.iconSize = iconSize;
            this.iconOnly = iconOnly;
        }

        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BUTTON.get(active, isHoveredOrFocused()),
                    getX(), getY(), getWidth(), getHeight(), ARGB.white(alpha));
            if (isHoveredOrFocused() && active) graphics.outline(getX(), getY(), getWidth(), getHeight(), ClientState.preferences.theme.accent);
            int iconTop = getY() + (getHeight() - iconSize) / 2;
            // A greyed button's icon fades with it.
            int tint = ARGB.white(active ? alpha : alpha * 0.4F);
            if (iconOnly) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, getX() + (getWidth() - iconSize) / 2, iconTop, iconSize, iconSize, tint);
                return;
            }
            ActiveTextCollector text = graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE);
            if (icon == null) {
                extractDefaultLabel(text);
                return;
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, getX() + 6, iconTop, iconSize, iconSize, tint);
            text.accept(TextAlignment.LEFT, getX() + 6 + iconSize + 6, getY() + (getHeight() - 8) / 2, getMessage());
        }
    }
}
