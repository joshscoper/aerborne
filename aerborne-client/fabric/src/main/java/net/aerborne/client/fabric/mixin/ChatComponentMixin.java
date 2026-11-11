package net.aerborne.client.fabric.mixin;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.aerborne.client.fabric.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Chat as a companion mod shows it: a timestamp on each line; your name highlighted with a soft ping; the same
 * line arriving again folded into the last one with a count; and coordinates followed by a [Track] link that
 * makes them a waypoint. Each part can be switched off in FeaturesScreen.
 */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow private void refreshTrimmedMessages() { }

    @Unique private static final DateTimeFormatter AERBORNE_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    @Unique private static final Pattern AERBORNE_COORDINATES =
            Pattern.compile("(?<![\\w.])(-?\\d{1,7})[ ,]+(-?\\d{1,4})[ ,]+(-?\\d{1,7})(?![\\w.])");
    @Unique private String aerborne$lastPlain;
    @Unique private int aerborne$repeats;

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component aerborne$decorate(Component message, Component original, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag) {
        var p = ClientState.preferences;
        if (!ClientState.available()) return message;
        String plain = message.getString();
        MutableComponent body = message.copy();

        // The same line again: drop the previous copy and count it.
        if (p.chatStack && plain.equals(aerborne$lastPlain) && !plain.isBlank() && !allMessages.isEmpty()) {
            aerborne$repeats++;
            allMessages.removeFirst();
            refreshTrimmedMessages();
            body.append(Component.literal(" (x" + aerborne$repeats + ")").withColor(0xFF8E9AAF));
        } else {
            aerborne$repeats = 1;
        }
        aerborne$lastPlain = plain;

        var mc = Minecraft.getInstance();
        if (p.chatMentions && mc.player != null) {
            String name = mc.player.getGameProfile().name();
            String lower = plain.toLowerCase(Locale.ROOT);
            // The name followed by a speaker mark ("Name:", "Name ›", "<Name>") is the player talking, not being mentioned.
            int at = lower.indexOf(name.toLowerCase(Locale.ROOT));
            String after = at < 0 ? "" : lower.substring(at + name.length()).stripLeading();
            boolean own = at >= 0 && (after.startsWith(":") || after.startsWith("›") || after.startsWith(">") || after.startsWith("»"));
            if (!own && name.length() >= 3 && lower.matches("(?s).*\\b" + Pattern.quote(name.toLowerCase(Locale.ROOT)) + "\\b.*")) {
                body = Component.literal("▍").withColor(0xFFF4C76B).append(body);
                if (aerborne$repeats == 1)
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6F, 0.6F));
            }
        }

        if (p.chatCoordinates) {
            Matcher match = AERBORNE_COORDINATES.matcher(plain);
            if (match.find()) {
                String command = "/aerborneclient waypoint " + match.group(1) + " " + match.group(2) + " " + match.group(3);
                body.append(Component.literal(" [Track]").withStyle(style -> style.withColor(0xFF8CE8ED)
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Make these coordinates your tracked waypoint")))));
            }
        }

        if (p.chatTimestamps)
            body = Component.literal(LocalTime.now().format(AERBORNE_TIME) + " ").withColor(0xFF5D6A82).append(body);
        return body;
    }
}
