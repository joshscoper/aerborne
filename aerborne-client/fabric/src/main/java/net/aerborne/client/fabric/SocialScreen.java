package net.aerborne.client.fabric;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Party and friends on one screen. The party side lists members with their health and distance (from the party feed),
 * with invite, leave or disband, and, for the leader, promote and kick. The friends side lists requests to answer and
 * friends (online first) with invite-to-party, message and remove. Every action is the existing server command
 * ({@code /party ...}, {@code /friends ...}); the server then sends a fresh snapshot ({@code /social refresh}).
 */
public final class SocialScreen extends AerborneScreen {
    private static final int CANVAS_W = 540, CANVAS_H = 340, ROW = 22, GREEN = 0xFF55E58A, GREY = 0xFF5D6A82;
    private FeedPayloads.Social social;
    private float scale = 1F;
    private EditBox inviteBox, friendBox;
    private int columnWidth, partyLeft, friendsLeft, listTop, friendScroll;
    private String confirmRemove = "";

    public SocialScreen(FeedPayloads.Social social) {
        super("Social", null);
        this.social = social;
    }

    void update(FeedPayloads.Social next) {
        social = next;
        rebuildWidgets();
    }

    private MouseButtonEvent canvas(MouseButtonEvent event) {
        return new MouseButtonEvent(event.x() / scale, event.y() / scale, event.buttonInfo());
    }

    private void command(String command) {
        if (minecraft.getConnection() == null) return;
        minecraft.getConnection().sendCommand(command);
        // Ask for a fresh snapshot once the server has acted.
        new Thread(() -> {
            try { Thread.sleep(400); } catch (InterruptedException ignored) { return; }
            minecraft.execute(() -> { if (minecraft.gui.screen() == this && minecraft.getConnection() != null) minecraft.getConnection().sendCommand("social refresh"); });
        }, "aerborne-social-refresh").start();
    }

    @Override protected void init() {
        int realWidth = minecraft.getWindow().getGuiScaledWidth(), realHeight = minecraft.getWindow().getGuiScaledHeight();
        scale = Math.min(1F, Math.min((realHeight - 8) / (float) CANVAS_H, (realWidth - 8) / (float) CANVAS_W));
        width = Math.round(realWidth / scale);
        height = Math.round(realHeight / scale);
        frame(CANVAS_W, CANVAS_H);
        columnWidth = (panelWidth - 42) / 2;
        partyLeft = left + 14;
        friendsLeft = partyLeft + columnWidth + 14;
        listTop = top + 44;
        int bottom = top + panelHeight - 30;

        // Party: invite field and the actions that fit the player's role.
        String invite = inviteBox == null ? "" : inviteBox.getValue();
        inviteBox = field(partyLeft, bottom - 26, columnWidth - 70, "Invite a player…", invite);
        button(partyLeft + columnWidth - 64, bottom - 28, 64, "Invite", () -> {
            String name = inviteBox.getValue().strip();
            if (!name.isEmpty()) {
                if (social.partyRole() == 0) command("party create");
                command("party invite " + name);
                inviteBox.setValue("");
            }
        });
        if (social.partyRole() == 0) button(partyLeft, bottom, 110, "Create party", () -> command("party create"));
        else {
            button(partyLeft, bottom, 90, "Leave", () -> command("party leave"));
            if (social.partyRole() == 2) button(partyLeft + 96, bottom, 90, "Disband", () -> command("party disband"));
        }
        // Leader controls beside each member.
        var party = AerborneSocial.party;
        if (social.partyRole() == 2 && party != null) {
            int y = listTop + 22;
            for (FeedPayloads.Member member : party.members()) {
                if (y > bottom - 50) break;
                String name = member.name();
                small(partyLeft + columnWidth - 58, y + 2, 28, "★", "Make " + name + " the leader", () -> command("party promote " + name));
                small(partyLeft + columnWidth - 28, y + 2, 28, "×", "Remove " + name + " from the party", () -> command("party kick " + name));
                y += ROW;
            }
        }

        // Friends: add field, then a row of actions per request and per friend.
        String add = friendBox == null ? "" : friendBox.getValue();
        friendBox = field(friendsLeft, bottom - 26, columnWidth - 70, "Add a friend…", add);
        button(friendsLeft + columnWidth - 64, bottom - 28, 64, "Add", () -> {
            String name = friendBox.getValue().strip();
            if (!name.isEmpty()) { command("friends add " + name); friendBox.setValue(""); }
        });
        int y = listTop;
        for (String request : social.requests()) {
            if (y > bottom - 50) break;
            small(friendsLeft + columnWidth - 58, y + 2, 28, "✔", "Accept " + request, () -> command("friends accept " + request));
            small(friendsLeft + columnWidth - 28, y + 2, 28, "×", "Decline " + request, () -> command("friends deny " + request));
            y += ROW;
        }
        if (!social.requests().isEmpty()) y += 8;
        List<FeedPayloads.Friend> friends = social.friends();
        int shown = Math.max(0, (bottom - 50 - y) / ROW);
        friendScroll = Math.max(0, Math.min(friendScroll, friends.size() - shown));
        for (int i = friendScroll; i < friends.size() && i < friendScroll + shown; i++) {
            FeedPayloads.Friend friend = friends.get(i);
            int fx = friendsLeft + columnWidth - 88;
            small(fx, y + 2, 28, "+", "Invite " + friend.name() + " to your party", () -> {
                if (social.partyRole() == 0) command("party create");
                command("party invite " + friend.name());
            });
            small(fx + 30, y + 2, 28, "✉", "Message " + friend.name(), () -> {
                minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("/msg " + friend.name() + " ", false));
            });
            boolean confirming = confirmRemove.equals(friend.name());
            small(fx + 60, y + 2, 28, confirming ? "?" : "−", confirming ? "Click again to remove " + friend.name() : "Remove " + friend.name(), () -> {
                if (confirmRemove.equals(friend.name())) { confirmRemove = ""; command("friends remove " + friend.name()); }
                else { confirmRemove = friend.name(); rebuildWidgets(); }
            });
            y += ROW;
        }
    }

    private EditBox field(int x, int y, int w, String hint, String value) {
        var box = new EditBox(font, x + 20, y + 3, w - 24, 12, Component.literal(hint)) {
            @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
                g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, isFocused() ? Ui.SEARCH_FOCUSED : Ui.SEARCH,
                        getX() - 20, getY() - 4, getWidth() + 24, 19);
                super.extractWidgetRenderState(g, mx, my, delta);
            }
        };
        box.setBordered(false);
        box.setTextColor(Ui.TEXT);
        box.setMaxLength(16);
        box.setHint(Component.literal(hint).withColor(Ui.MUTED));
        box.setValue(value);
        return addRenderableWidget(box);
    }

    private void small(int x, int y, int w, String label, String tip, Runnable action) {
        var b = button(x, y, w, label, action);
        b.setHeight(16);
        b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tip), java.util.Optional.empty(),
                net.minecraft.resources.Identifier.fromNamespaceAndPath("aerborne_client", "menu")));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        super.extractRenderState(g, Math.round(mx / scale), Math.round(my / scale), delta);
        g.pose().popMatrix();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { return super.mouseClicked(canvas(event), doubleClick); }
    @Override public boolean mouseReleased(MouseButtonEvent event) { return super.mouseReleased(canvas(event)); }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        mx /= scale;
        if (mx >= friendsLeft) { friendScroll = Math.max(0, friendScroll - (int) Math.signum(dy)); rebuildWidgets(); return true; }
        return super.mouseScrolled(mx, my / scale, dx, dy);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        boolean typing = inviteBox != null && inviteBox.isFocused() || friendBox != null && friendBox.isFocused();
        if (typing && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            if (inviteBox.isFocused() && !inviteBox.getValue().isBlank()) { command("party invite " + inviteBox.getValue().strip()); inviteBox.setValue(""); }
            if (friendBox.isFocused() && !friendBox.getValue().isBlank()) { command("friends add " + friendBox.getValue().strip()); friendBox.setValue(""); }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override protected void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        // Two inset columns with their headings.
        int bottom = top + panelHeight - 30;
        Ui.inset(g, partyLeft - 4, listTop - 22, columnWidth + 8, bottom - 34 - (listTop - 22));
        Ui.inset(g, friendsLeft - 4, listTop - 22, columnWidth + 8, bottom - 34 - (listTop - 22));
        Ui.icon(g, Ui.sprite("menu_icons/party"), partyLeft + 2, listTop - 18, 16);
        g.text(font, Ui.heading("Party"), partyLeft + 22, listTop - 18, Ui.GOLD, true);
        String size = social.partyRole() == 0 ? "not in a party" : social.partySize() + " / 8" + (social.partyRole() == 2 ? " · you lead" : "");
        g.text(font, size, partyLeft + columnWidth - font.width(size), listTop - 15, Ui.MUTED, false);
        Ui.icon(g, Ui.sprite("menu_icons/friends"), friendsLeft + 2, listTop - 18, 16);
        g.text(font, Ui.heading("Friends"), friendsLeft + 22, listTop - 18, Ui.GOLD, true);
        long online = social.friends().stream().filter(FeedPayloads.Friend::online).count();
        String count = online + " online · " + social.friends().size();
        g.text(font, count, friendsLeft + columnWidth - font.width(count), listTop - 15, Ui.MUTED, false);

        // Party members: yourself first, then the feed's members.
        int y = listTop;
        var self = minecraft.player;
        if (social.partyRole() == 0) {
            g.textWithWordWrap(font, Component.literal("Invite someone below to start a party, or accept an invite from a friend."),
                    partyLeft + 2, y + 4, columnWidth - 4, Ui.MUTED);
        } else {
            memberRow(g, y, (social.partyRole() == 2 ? "★ " : "") + self.getGameProfile().name() + "  (you)", true,
                    self.getHealth() / Math.max(1F, self.getMaxHealth()), "");
            y += ROW;
            var party = AerborneSocial.party;
            if (party != null) for (FeedPayloads.Member member : party.members()) {
                if (y > bottom - 50) break;
                String where = !member.online() ? "offline" : member.distance() < 0 ? "away" : member.distance() + "m";
                memberRow(g, y, (member.leader() ? "★ " : "") + member.name(), member.online(),
                        member.online() && member.maxHealth() > 0 ? member.health() / member.maxHealth() : 0, where);
                y += ROW;
            }
        }

        // Requests, then friends.
        y = listTop;
        for (String request : social.requests()) {
            if (y > bottom - 50) break;
            g.fill(friendsLeft, y, friendsLeft + columnWidth, y + ROW - 2, 0x40F4C76B);
            g.text(font, Ui.fit(font, request + " wants to be friends", columnWidth - 66), friendsLeft + 4, y + 6, Ui.GOLD, false);
            y += ROW;
        }
        if (!social.requests().isEmpty()) y += 8;
        List<FeedPayloads.Friend> friends = social.friends();
        if (friends.isEmpty()) g.textWithWordWrap(font, Component.literal("No friends yet. Add someone by name below."),
                friendsLeft + 2, y + 4, columnWidth - 4, Ui.MUTED);
        int shown = Math.max(0, (bottom - 50 - y) / ROW);
        for (int i = friendScroll; i < friends.size() && i < friendScroll + shown; i++) {
            FeedPayloads.Friend friend = friends.get(i);
            g.fill(friendsLeft + 3, y + 7, friendsLeft + 8, y + 12, friend.online() ? GREEN : GREY);
            g.text(font, Ui.fit(font, friend.name(), columnWidth - 104), friendsLeft + 13, y + 6, friend.online() ? Ui.TEXT : Ui.MUTED, false);
            y += ROW;
        }
    }

    private void memberRow(GuiGraphicsExtractor g, int y, String name, boolean online, float health, String where) {
        int leaderControls = social.partyRole() == 2 && !name.endsWith("(you)") ? 62 : 0;
        int width = columnWidth - leaderControls;
        g.text(font, Ui.fit(font, name, width - 40), partyLeft + 2, y + 1, online ? Ui.TEXT : Ui.MUTED, false);
        if (!where.isEmpty()) g.text(font, where, partyLeft + width - 4 - font.width(where), y + 1, Ui.MUTED, false);
        Ui.bar(g, partyLeft + 2, y + 11, width - 6, 7, Ui.HEALTH, online ? health : 0);
    }
}
