package net.aerborne.client.fabric;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * An NPC conversation in the Aerborne frame, along the bottom of the screen so the speaker stays in view. The text
 * writes itself out; a click or Space shows it all at once. Replies are buttons, also chosen with 1 to 5, and go back
 * to the server as {@code /npc reply}; Esc or "Leave" ends the conversation with {@code /npc bye}.
 */
public final class DialogueScreen extends Screen {
    private static final Identifier PANEL = Identifier.fromNamespaceAndPath("aerborne_client", "menu/panel");
    private static final Identifier TOOLTIP_STYLE = Identifier.fromNamespaceAndPath("aerborne_client", "menu");
    private static final int GOLD = 0xFFF4C76B, CLOUD = 0xFFF4F9FF, MUTED = 0xFF8E9AAF;
    /** Characters revealed per second. */
    private static final float SPEED = 70F;

    private final DialoguePayload page;
    private final long opened = System.nanoTime();
    private boolean revealedAll, answered;
    private List<String> lines = List.of();
    private int totalChars;
    private int left, top, panelWidth, panelHeight, textTop;

    DialogueScreen(DialoguePayload page) {
        super(Component.literal(page.name()));
        this.page = page;
    }

    String token() { return page.token(); }

    @Override protected void init() {
        panelWidth = Math.min(440, width - 24);
        lines = wrap(page.body(), panelWidth - 28);
        totalChars = lines.stream().mapToInt(String::length).sum();
        int bodyHeight = Math.max(1, lines.size()) * 11;
        int replies = page.replies().size() + 1;
        int columns = panelWidth >= 360 ? 2 : 1;
        int rows = (replies + columns - 1) / columns;
        panelHeight = 30 + bodyHeight + 10 + rows * 24 + 8;
        left = (width - panelWidth) / 2;
        top = Math.max(8, height - panelHeight - 52);
        textTop = top + 30;
        int buttonTop = textTop + bodyHeight + 10;
        int buttonWidth = (panelWidth - 28 - (columns - 1) * 6) / columns;
        for (int i = 0; i < replies; i++) {
            int x = left + 14 + (i % columns) * (buttonWidth + 6), y = buttonTop + (i / columns) * 24;
            if (i == page.replies().size()) {
                addRenderableWidget(new AerborneMenuScreen.SpriteButton(x, y, buttonWidth, 20,
                        Component.literal("Leave"), null, 0, false, button -> leave()));
                continue;
            }
            DialoguePayload.Reply reply = page.replies().get(i);
            int number = i + 1;
            AerborneMenuScreen.SpriteButton button = addRenderableWidget(new AerborneMenuScreen.SpriteButton(x, y, buttonWidth, 20,
                    Component.literal(number + ". " + font.plainSubstrByWidth(reply.text(), buttonWidth - 24))
                            .withColor(reply.highlighted() ? GOLD : CLOUD),
                    null, 0, false, b -> answer(number)));
            button.active = reply.available();
            List<String> details = new ArrayList<>(reply.details());
            if (font.width(reply.text()) > buttonWidth - 24) details.addFirst(reply.text());
            if (!reply.available() && details.isEmpty()) details.add("Unavailable");
            if (!details.isEmpty())
                button.setTooltip(Tooltip.create(Component.literal(String.join("\n", details)), Optional.empty(), TOOLTIP_STYLE));
        }
    }

    private List<String> wrap(List<String> paragraphs, int width) {
        List<String> out = new ArrayList<>();
        for (String paragraph : paragraphs) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split("\\s+")) {
                if (word.isEmpty()) continue;
                String next = line.isEmpty() ? word : line + " " + word;
                if (font.width(next) > width && !line.isEmpty()) { out.add(line.toString()); line = new StringBuilder(word); }
                else { line.setLength(0); line.append(next); }
            }
            out.add(line.toString());
        }
        return out;
    }

    private int shownChars() {
        if (revealedAll) return totalChars;
        int shown = (int) ((System.nanoTime() - opened) / 1_000_000_000D * SPEED);
        if (shown >= totalChars) revealedAll = true;
        return Math.min(shown, totalChars);
    }

    private void answer(int number) {
        if (answered || number > page.replies().size() || !page.replies().get(number - 1).available()) return;
        if (!revealedAll) { revealedAll = true; return; }
        answered = true;
        if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand("npc reply " + page.token() + " " + number);
        // The next page replaces this screen; if there is none, the server's close message (or Esc) ends it.
        for (var child : children()) if (child instanceof AerborneMenuScreen.SpriteButton button) button.active = false;
    }

    private void leave() {
        if (!answered && minecraft.getConnection() != null) minecraft.getConnection().sendCommand("npc bye " + page.token());
        answered = true;
        minecraft.gui.setScreen(null);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) { leave(); return true; }
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) { revealedAll = true; return true; }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_5) { answer(key - GLFW.GLFW_KEY_1 + 1); return true; }
        return super.keyPressed(event);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!revealedAll && event.y() < textTop + lines.size() * 11 + 6 && event.y() >= top) { revealedAll = true; return true; }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Keep the world in view: only a light shade at the bottom behind the panel.
        graphics.fillGradient(0, top - 40, width, height, 0x00000000, 0x90000000);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, left, top, panelWidth, panelHeight);
        // The speaker's name on a tab over the panel's top edge.
        int nameWidth = font.width(page.name());
        graphics.fill(left + 12, top - 8, left + 24 + nameWidth, top + 8, 0xFF17243B);
        graphics.outline(left + 12, top - 8, nameWidth + 12, 16, GOLD);
        graphics.text(font, page.name(), left + 18, top - 4, GOLD, true);
        if (!page.title().isEmpty()) graphics.text(font, page.title(), left + 30 + nameWidth, top - 4, MUTED, true);
        int remaining = shownChars();
        int y = textTop - 12;
        for (String line : lines) {
            y += 11;
            if (remaining <= 0) break;
            String shown = line.length() <= remaining ? line : line.substring(0, remaining);
            remaining -= line.length();
            graphics.text(font, shown, left + 14, y, CLOUD, false);
        }
        if (revealedAll && !answered) {
            String hint = "1-" + page.replies().size() + " to reply  ·  Esc to leave";
            if (page.replies().isEmpty()) hint = "Esc to leave";
            graphics.text(font, hint, left + panelWidth - 14 - font.width(hint), top + 12, MUTED, false);
        } else if (!revealedAll) {
            String hint = "Click or Space to skip";
            graphics.text(font, hint, left + panelWidth - 14 - font.width(hint), top + 12, MUTED, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override public boolean isPauseScreen() { return false; }
}
