package net.aerborne.client.fabric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The quest book: quests on the left, grouped as tracked, ready to turn in, active, available and completed; the
 * chosen quest on the right with its story, objectives and rewards. Track and Abandon go to the server as
 * {@code /journal track} and {@code /journal abandon}; the server answers with a fresh book.
 */
public final class QuestBookScreen extends AerborneScreen {
    private static final int TEXT = 0xFFF4F9FF, MUTED = 0xFF8E9AAF, GOLD = 0xFFF4C76B, GREEN = 0xFF55E58A, ROW = 13;
    private static final String[] SECTIONS = {"Available", "Active", "Ready to turn in", "Completed"};

    private FeedPayloads.Quests book;
    private String selected;
    private int scroll;
    private boolean confirmAbandon;
    /** The list as drawn: a section header (quest null) or a quest. */
    private record Row(String header, FeedPayloads.Quest quest) { }
    private final List<Row> rows = new ArrayList<>();
    private int listLeft, listTop, listWidth, listHeight;

    public QuestBookScreen(FeedPayloads.Quests book) {
        super("Quest Book", null);
        this.book = book;
        this.selected = !book.tracked().isEmpty() ? book.tracked()
                : book.quests().stream().filter(q -> q.status() == 1 || q.status() == 2).map(FeedPayloads.Quest::id).findFirst()
                .orElse(book.quests().isEmpty() ? null : book.quests().getFirst().id());
    }

    /** A fresh book from the server (after tracking or abandoning) keeps the same quest selected. */
    void update(FeedPayloads.Quests next) {
        book = next;
        if (next.quests().stream().noneMatch(q -> q.id().equals(selected)))
            selected = next.quests().isEmpty() ? null : next.quests().getFirst().id();
        confirmAbandon = false;
        rebuildWidgets();
    }

    private FeedPayloads.Quest current() {
        return book.quests().stream().filter(q -> q.id().equals(selected)).findFirst().orElse(null);
    }

    @Override protected void init() {
        frame(560, 320);
        listLeft = left + 12; listTop = top + 42; listWidth = 176; listHeight = panelHeight - 42 - 40;
        rows.clear();
        // Tracked first, then ready, active, available and completed.
        int[] order = {2, 1, 0, 3};
        for (FeedPayloads.Quest quest : book.quests()) if (quest.id().equals(book.tracked())) { rows.add(new Row("Tracked", null)); rows.add(new Row(null, quest)); }
        for (int status : order) {
            List<FeedPayloads.Quest> group = book.quests().stream().filter(q -> q.status() == status && !q.id().equals(book.tracked())).toList();
            if (group.isEmpty()) continue;
            rows.add(new Row(SECTIONS[status] + " (" + group.size() + ")", null));
            for (FeedPayloads.Quest quest : group) rows.add(new Row(null, quest));
        }
        scroll = Math.max(0, Math.min(scroll, rows.size() - listHeight / ROW));

        FeedPayloads.Quest quest = current();
        int buttonY = top + panelHeight - 30;
        if (quest != null && (quest.status() == 1 || quest.status() == 2)) {
            boolean tracked = quest.id().equals(book.tracked());
            button(left + panelWidth - 12 - 120, buttonY, 120, tracked ? "Hide trail" : "Track", () ->
                    command(tracked ? "journal untrack" : "journal track " + quest.id()));
            button(left + panelWidth - 12 - 120 - 8 - 120, buttonY, 120, confirmAbandon ? "Really abandon?" : "Abandon", () -> {
                if (!confirmAbandon) { confirmAbandon = true; rebuildWidgets(); return; }
                command("journal abandon " + quest.id());
            });
        }
    }

    private void command(String command) {
        if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand(command);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (mx >= listLeft && mx < listLeft + listWidth && my >= listTop && my < listTop + listHeight) {
            int index = scroll + (int) ((my - listTop) / ROW);
            if (index >= 0 && index < rows.size() && rows.get(index).quest() != null) {
                selected = rows.get(index).quest().id();
                confirmAbandon = false;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (mx >= listLeft && mx < listLeft + listWidth) {
            scroll = Math.max(0, Math.min(rows.size() - listHeight / ROW, scroll - (int) Math.signum(dy) * 2));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override protected void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        String summary = book.completed() + " completed";
        g.text(font, summary, left + panelWidth - 14 - font.width(summary), top + 14, MUTED);
        // The list.
        g.fill(listLeft - 2, listTop - 2, listLeft + listWidth + 2, listTop + listHeight + 2, 0xFF0D1A2B);
        int visible = listHeight / ROW;
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int y = listTop + i * ROW;
            if (row.quest() == null) { g.text(font, row.header(), listLeft + 2, y + 3, ClientState.preferences.theme.accent, false); continue; }
            FeedPayloads.Quest quest = row.quest();
            boolean chosen = quest.id().equals(selected);
            boolean hover = mx >= listLeft && mx < listLeft + listWidth && my >= y && my < y + ROW;
            if (chosen || hover) g.fill(listLeft, y, listLeft + listWidth, y + ROW, chosen ? 0xFF283752 : 0xFF1A2A44);
            String mark = switch (quest.status()) { case 2 -> "? "; case 1 -> "• "; case 0 -> "! "; default -> "✔ "; };
            int colour = switch (quest.status()) { case 2 -> GREEN; case 0 -> GOLD; case 3 -> MUTED; default -> TEXT; };
            g.text(font, mark + font.plainSubstrByWidth(quest.title(), listWidth - 16), listLeft + 4, y + 3, colour, false);
        }
        if (rows.size() > visible) {
            int bar = Math.max(12, listHeight * visible / rows.size());
            int at = listTop + (listHeight - bar) * scroll / Math.max(1, rows.size() - visible);
            g.fill(listLeft + listWidth, at, listLeft + listWidth + 2, at + bar, 0xFF4A5A75);
        }
        if (rows.isEmpty()) g.text(font, "No quests yet.", listLeft + 4, listTop + 4, MUTED, false);

        // The chosen quest.
        FeedPayloads.Quest quest = current();
        if (quest == null) return;
        int x = listLeft + listWidth + 14, width = left + panelWidth - 14 - x, y = listTop;
        g.text(font, font.plainSubstrByWidth(quest.title(), width), x, y, GOLD, true);
        y += 12;
        String meta = (quest.chain().isEmpty() ? "" : quest.chain() + " · ") + (quest.daily() ? "Daily · " : "")
                + "Level " + quest.minimumLevel() + " · " + (quest.status() == 2 ? "Return to " + quest.turnIn() : "From " + quest.giver());
        for (var wrapped : font.split(Component.literal(meta), width)) { g.text(font, wrapped, x, y, MUTED, false); y += 10; }
        y += 4;

        // Objectives and rewards are laid out from the bottom up, so the story gets whatever room is left.
        List<String> rewards = new ArrayList<>();
        if (quest.marks() > 0) rewards.add(quest.marks() + " Marks");
        for (FeedPayloads.Reward reward : quest.experience())
            rewards.add(reward.amount() + " " + reward.skill().substring(0, 1).toUpperCase(Locale.ROOT) + reward.skill().substring(1).toLowerCase(Locale.ROOT) + " XP");
        rewards.addAll(quest.items());
        var rewardLines = rewards.isEmpty() ? List.<net.minecraft.util.FormattedCharSequence>of() : font.split(Component.literal(String.join("  ·  ", rewards)), width);
        List<List<net.minecraft.util.FormattedCharSequence>> objectiveLines = new ArrayList<>();
        int objectivesHeight = 18;
        for (FeedPayloads.Objective objective : quest.objectives()) {
            boolean done = objective.have() >= objective.count();
            // A zone the server could not name arrives as its raw id; leave that out.
            boolean named = !objective.zone().isEmpty() && !objective.zone().matches("[a-z0-9_]+");
            String text = (done ? "✔ " : "• ") + objective.description() + (named ? " (" + objective.zone() + ")" : "");
            var lines = font.split(Component.literal(text), width - 34);
            objectiveLines.add(lines);
            objectivesHeight += lines.size() * 10 + 2 + (objective.count() > 1 ? 4 : 0);
        }
        int bottom = listTop + listHeight;
        int rewardsHeight = rewardLines.isEmpty() ? 0 : 18 + rewardLines.size() * 10;
        int storyEnd = bottom - objectivesHeight - rewardsHeight;
        for (String line : quest.offer()) {
            for (var wrapped : font.split(Component.literal(line), width)) {
                if (y + 10 > storyEnd) break;
                g.text(font, wrapped, x, y, 0xFFD6DEEA, false);
                y += 10;
            }
        }
        y += 8;
        g.text(font, "Objectives", x, y, ClientState.preferences.theme.accent, false);
        y += 12;
        for (int i = 0; i < quest.objectives().size(); i++) {
            FeedPayloads.Objective objective = quest.objectives().get(i);
            boolean done = objective.have() >= objective.count();
            int top = y;
            for (var wrapped : objectiveLines.get(i)) { g.text(font, wrapped, x, y, done ? GREEN : TEXT, false); y += 10; }
            if (objective.count() > 1 || quest.status() != 0) {
                String count = Math.min(objective.have(), objective.count()) + "/" + objective.count();
                g.text(font, count, x + width - font.width(count), top, done ? GREEN : MUTED, false);
            }
            if (objective.count() > 1) {
                g.fill(x, y, x + width, y + 2, 0xFF283752);
                g.fill(x, y, x + Math.round(width * Math.min(1F, objective.have() / (float) objective.count())), y + 2, done ? GREEN : GOLD);
                y += 4;
            }
            y += 2;
        }
        y += 6;
        if (!rewardLines.isEmpty()) {
            g.text(font, "Rewards", x, y, ClientState.preferences.theme.accent, false);
            y += 12;
            for (var wrapped : rewardLines) {
                g.text(font, wrapped, x, y, GOLD, false);
                y += 10;
            }
        }
    }
}
