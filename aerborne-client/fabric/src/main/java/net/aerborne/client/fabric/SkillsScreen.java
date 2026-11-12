package net.aerborne.client.fabric;

import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The Skills screen: the Adventurer's level and rank across the top, then a card per skill with its icon, level, XP,
 * mastery and talent points. A card opens that skill's talent tree on the server ({@code /skills <skill>}); the
 * Adventurer plate opens the Adventurer tree. Laid out on a fixed canvas and scaled to the screen, like the quest book.
 */
public final class SkillsScreen extends AerborneScreen {
    private static final int CANVAS_W = 540, CANVAS_H = 372, CARD_W = 162, CARD_H = 44, GAP = 8;
    private final FeedPayloads.Skills skills;
    private float scale = 1F;
    private int gridLeft, gridTop, columns;

    public SkillsScreen(FeedPayloads.Skills skills) {
        super("Skills", null);
        this.skills = skills;
    }

    private MouseButtonEvent canvas(MouseButtonEvent event) {
        return new MouseButtonEvent(event.x() / scale, event.y() / scale, event.buttonInfo());
    }

    @Override protected void init() {
        int realWidth = minecraft.getWindow().getGuiScaledWidth(), realHeight = minecraft.getWindow().getGuiScaledHeight();
        scale = Math.min(1F, Math.min((realHeight - 8) / (float) CANVAS_H, (realWidth - 8) / (float) CANVAS_W));
        width = Math.round(realWidth / scale);
        height = Math.round(realHeight / scale);
        frame(CANVAS_W, CANVAS_H);
        columns = 3;
        gridLeft = left + (panelWidth - (columns * CARD_W + (columns - 1) * GAP)) / 2;
        gridTop = top + 74;
        for (int i = 0; i < skills.skills().size(); i++) {
            FeedPayloads.Skill skill = skills.skills().get(i);
            int x = gridLeft + (i % columns) * (CARD_W + GAP), y = gridTop + (i / columns) * (CARD_H + 6);
            var card = addRenderableWidget(new Card(x, y, skill));
            card.setTooltip(Tooltip.create(Component.literal(skill.hint() + "\n\nClick to open the " + skill.name() + " talent tree."),
                    Optional.empty(), Identifier("menu")));
        }
    }

    private static net.minecraft.resources.Identifier Identifier(String path) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath("aerborne_client", path);
    }

    private void command(String command) {
        if (minecraft.getConnection() == null) return;
        onClose();
        minecraft.getConnection().sendCommand(command);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        super.extractRenderState(g, Math.round(mx / scale), Math.round(my / scale), delta);
        g.pose().popMatrix();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent at = canvas(event);
        // The Adventurer plate opens the Adventurer tree.
        if (at.x() >= left + 14 && at.x() < left + panelWidth - 14 && at.y() >= top + 24 && at.y() < top + 66) {
            command("adventurer");
            return true;
        }
        return super.mouseClicked(at, doubleClick);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) { return super.mouseReleased(canvas(event)); }

    @Override protected void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        // The Adventurer plate.
        int x = left + 14, y = top + 24, w = panelWidth - 28;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 42;
        Ui.inset(g, x, y, w, 42);
        if (hover) g.outline(x + 1, y + 1, w - 2, 40, Ui.GOLD);
        Ui.icon(g, Ui.SLOT, x + 3, y + 3, 36);
        Ui.icon(g, Ui.icon("skills", "adventurer"), x + 5, y + 5, 32);
        g.text(font, Ui.heading("Adventurer " + skills.adventurerLevel()), x + 48, y + 6, Ui.GOLD, true);
        g.text(font, skills.rank(), x + 48, y + 20, Ui.MUTED, false);
        int barX = x + 160, barW = w - 168;
        boolean max = skills.adventurerNeeded() <= 0;
        Ui.bar(g, barX, y + 17, barW, 12, Ui.ADVENTURER_XP, max ? 1 : skills.adventurerXp() / (float) skills.adventurerNeeded());
        String label = max ? "Maximum level" : AerborneHud.compact(skills.adventurerXp()) + " / " + AerborneHud.compact(skills.adventurerNeeded()) + " XP";
        g.text(font, label, barX + barW / 2 - font.width(label) / 2, y + 19, Ui.TEXT, true);
    }

    /** A skill card: a button whose face is drawn by {@link #card}. */
    private final class Card extends net.minecraft.client.gui.components.Button {
        private final FeedPayloads.Skill skill;

        Card(int x, int y, FeedPayloads.Skill skill) {
            super(x, y, CARD_W, CARD_H, Component.literal(skill.name()), b -> command("skills " + skill.id()), DEFAULT_NARRATION);
            this.skill = skill;
        }

        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
            card(g, skill, getX(), getY(), isHoveredOrFocused());
        }
    }

    private void card(GuiGraphicsExtractor g, FeedPayloads.Skill skill, int x, int y, boolean hover) {
        Ui.inset(g, x, y, CARD_W, CARD_H);
        if (hover) g.outline(x + 1, y + 1, CARD_W - 2, CARD_H - 2, Ui.GOLD);
        // The icon sits in a framed slot, drawn at twice its size.
        Ui.icon(g, Ui.SLOT, x + 4, y + 4, 36);
        Ui.icon(g, Ui.icon("skills", skill.id()), x + 6, y + 6, 32);
        int tx = x + 44;
        g.text(font, Ui.heading(skill.name()), tx, y + 5, skill.mastered() ? Ui.GOLD_LIGHT : Ui.GOLD, true);
        boolean max = skill.level() >= skill.maxLevel() || skill.xpNeeded() <= 0;
        Ui.bar(g, tx, y + 19, CARD_W - 51, 9, Ui.SKILL_XP, max ? 1 : skill.xp() / (float) skill.xpNeeded());
        String xp = "Lv " + skill.level() + "  ·  " + (max ? "MAX" : AerborneHud.compact(skill.xp()) + " / " + AerborneHud.compact(skill.xpNeeded()) + " XP");
        g.pose().pushMatrix();
        g.pose().translate(tx + (CARD_W - 51) / 2F, y + 20);
        g.pose().scale(0.75F, 0.75F);
        g.text(font, xp, -font.width(xp) / 2, 0, Ui.TEXT, true);
        g.pose().popMatrix();
        String foot = (skill.mastered() ? "★ Mastered" : "") + (skill.pointsReady() > 0 ? (skill.mastered() ? "  " : "") + skill.pointsReady() + " point" + (skill.pointsReady() == 1 ? "" : "s") + " ready" : "");
        if (foot.isEmpty()) foot = skill.pointsSpent() + " talent point" + (skill.pointsSpent() == 1 ? "" : "s") + " spent";
        g.text(font, font.plainSubstrByWidth(foot, CARD_W - 51), tx, y + 31, skill.pointsReady() > 0 || skill.mastered() ? Ui.GOLD : Ui.MUTED, false);
    }
}
