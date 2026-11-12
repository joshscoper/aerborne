package net.aerborne.client.protocol;

/**
 * Layout shared by native server menus. Server pages and local display pages are separate. Entries are drawn either as
 * cards (an icon, a name and two lines of detail; up to three columns) or, for rankings and other long lists, as
 * table rows (one line each; one or two columns).
 */
public record MenuLayout(int width, int height, int columns, int rows, int toolColumns, int toolRows,
                         int navColumns, int navRows, int bodyTop, int bodyBottom, int entries, boolean table) {
    public record Box(int x, int y, int width, int height) { }
    private static final int CARD = 48, CARD_STEP = 52, ROW = 22, ROW_STEP = 24;

    public static MenuLayout of(int width, int height, int tools, int navigation, int entries) {
        return of(width, height, tools, navigation, entries, false);
    }

    public static MenuLayout of(int width, int height, int tools, int navigation, int entries, boolean table) {
        width = Math.max(360, width); height = Math.max(280, height);
        int controlColumns = Math.max(1, (width - 28) / 96);
        // A short row of controls spreads out (to 160 each) instead of crowding the left in narrow buttons.
        int toolColumns = Math.clamp(tools, 1, controlColumns), navColumns = Math.clamp(navigation, 1, controlColumns);
        int toolRows = (tools + toolColumns - 1) / toolColumns;
        int navRows = (navigation + navColumns - 1) / navColumns;
        int bodyTop = 48 + toolRows * 24, bodyBottom = height - 40 - navRows * 24;
        int columns = table ? (width >= 500 ? 2 : 1) : width >= 560 ? 3 : width >= 400 ? 2 : 1;
        int rows = Math.max(1, (bodyBottom - bodyTop - 8) / (table ? ROW_STEP : CARD_STEP));
        return new MenuLayout(width, height, columns, rows, toolColumns, toolRows,
                navColumns, navRows, bodyTop, bodyBottom, entries, table);
    }
    public int perPage() { return columns * rows; }
    public int pages() { return Math.max(1, (entries + perPage() - 1) / perPage()); }
    public int clampPage(int page) { return Math.clamp(page, 0, pages() - 1); }
    /** Entry {@code visibleIndex} on the current page; tables fill each column top to bottom, cards fill rows. */
    public Box card(int visibleIndex) {
        if (visibleIndex < 0 || visibleIndex >= perPage()) throw new IndexOutOfBoundsException(visibleIndex);
        int w = (width - 28 - (columns - 1) * 8) / columns;
        if (table) {
            int column = visibleIndex / rows, row = visibleIndex % rows;
            return new Box(14 + column * (w + 8), bodyTop + row * ROW_STEP, w, ROW);
        }
        return new Box(14 + visibleIndex % columns * (w + 8), bodyTop + visibleIndex / columns * CARD_STEP, w, CARD);
    }
    public Box tool(int index) { return control(index, toolColumns, 26); }
    public Box navigation(int index) { return control(index, navColumns, bodyBottom + 4); }
    private Box control(int index, int columns, int y) {
        int w = Math.min(160, (width - 28 - (columns - 1) * 6) / columns);
        return new Box(14 + index % columns * (w + 6), y + index / columns * 24, w, 20);
    }
    public static boolean auctionBrowse(String kind, String title, int slots) {
        return kind.contains("AuctionController$") && slots == 54
                && (title.equals("Auction House") || title.equals("My Auctions"));
    }
}
