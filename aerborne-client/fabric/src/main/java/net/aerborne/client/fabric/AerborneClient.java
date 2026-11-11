package net.aerborne.client.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class AerborneClient implements ClientModInitializer {
    private Runnable pendingAction;
    @Override public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(CapabilitiesPayload.TYPE, CapabilitiesPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(CapabilitiesPayload.TYPE,
                (payload, context) -> {
                    if (!ClientState.bridgeAvailable() && payload.capabilities().supportsMenu())
                        org.slf4j.LoggerFactory.getLogger("Aerborne").info("Server bridge connected");
                    ClientState.SESSION.receive(payload.capabilities(), System.nanoTime());
                });
        PayloadTypeRegistry.clientboundPlay().register(WorldPayload.TYPE, WorldPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(WorldPayload.TYPE, (payload, context) -> ClientState.world = payload.world());
        // Registering this receiver is what tells the server we draw our own HUD: it then stops its action bar and sidebar.
        PayloadTypeRegistry.clientboundPlay().register(HudPayload.TYPE, HudPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(HudPayload.TYPE, (payload, context) -> {
            AerborneExtras.onHud(payload);
            ClientState.hud = payload;
            ClientState.hudAt = System.nanoTime();
        });
        PayloadTypeRegistry.clientboundPlay().register(PlatesPayload.TYPE, PlatesPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(PlatesPayload.TYPE, (payload, context) -> ClientState.plates = payload);
        PayloadTypeRegistry.clientboundPlay().register(DialoguePayload.TYPE, DialoguePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(DialoguePayload.TYPE, (payload, context) -> {
            var client = context.client();
            if (payload.closes()) {
                if (client.gui.screen() instanceof DialogueScreen open && open.token().equals(payload.token())) client.gui.setScreen(null);
            } else if (client.gui.screen() == null || client.gui.screen() instanceof DialogueScreen) {
                client.gui.setScreen(new DialogueScreen(payload));
            }
        });
        // The server tags its own chest menus as they open; those are drawn in the Aerborne frame (AerborneMenus).
        PayloadTypeRegistry.clientboundPlay().register(MenuPayload.TYPE, MenuPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(MenuPayload.TYPE, (payload, context) -> AerborneMenus.tagged(context.client(), payload));
        PayloadTypeRegistry.clientboundPlay().register(FeedPayloads.Party.TYPE, FeedPayloads.Party.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(FeedPayloads.Party.TYPE, (payload, context) -> AerborneSocial.party = payload);
        PayloadTypeRegistry.clientboundPlay().register(FeedPayloads.Toast.TYPE, FeedPayloads.Toast.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(FeedPayloads.Toast.TYPE, (payload, context) -> AerborneSocial.toast(payload));
        PayloadTypeRegistry.clientboundPlay().register(FeedPayloads.Quests.TYPE, FeedPayloads.Quests.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(FeedPayloads.Quests.TYPE, (payload, context) -> {
            var client = context.client();
            if (client.gui.screen() instanceof QuestBookScreen open) open.update(payload);
            else if (payload.open()) client.gui.setScreen(new QuestBookScreen(payload));
        });
        ClientPlayConnectionEvents.INIT.register((handler, client) -> { ClientState.reset(); AerborneExtras.resetSession(); AerborneSocial.party = null; });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { ClientState.reset(); AerborneExtras.resetSession(); });
        // Our effect column replaces the vanilla icons while the Aerborne HUD is up.
        HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, vanilla -> (graphics, delta) -> {
            var p = ClientState.preferences;
            if (p.effects && p.hud && ClientState.available()) return;
            vanilla.extractRenderState(graphics, delta);
        });
        ClientConfigurationConnectionEvents.INIT.register((handler, client) -> ClientState.reset());
        ClientLifecycleEvents.CLIENT_STARTED.register(DefaultServer::seed);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.parse("aerborne_client:hud"),
                (graphics, delta) -> AerborneHud.draw(graphics));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            AerborneMenus.opened(screen);
            // Chest screens get a search box above their grid.
            if (screen instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen chest && ClientState.available()) {
                var at = (net.aerborne.client.fabric.mixin.AbstractContainerScreenAccessor) chest;
                AerborneMenus.addSearch(chest, at.aerborne$left(), at.aerborne$top(), at.aerborne$width());
            }
            if (screen instanceof TitleScreen) Screens.getWidgets(screen).add(Button.builder(Component.literal("Client appearance"),
                    button -> client.gui.setScreen(new AppearanceScreen(screen))).bounds(width - 128, 8, 120, 20).build());
        });
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("aerborne_client", "main"));
        KeyMapping menu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.aerborne_client.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_B, category));
        KeyMapping hud = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.aerborne_client.hud", InputConstants.Type.KEYSYM, InputConstants.KEY_H, category));
        KeyMapping waypoints = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.aerborne_client.waypoints", InputConstants.Type.KEYSYM, InputConstants.KEY_J, category));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) -> dispatcher.register(
                literal("aerborneclient").executes(context -> {
                    pendingAction = () -> openMenu(context.getSource().getClient()); return 1;
                }).then(literal("status").executes(context -> {
                    context.getSource().sendFeedback(Component.literal("Aerborne client: " + (available() ? "local tools ready" : "inactive on this server")
                            + ". Bridge: " + (ClientState.bridgeAvailable() ? "connected" : "not detected")
                            + ". World data: " + (!ClientState.scope().isEmpty() ? "ready" : "unavailable")
                            + ". Menu: " + menu.getTranslatedKeyMessage().getString()
                            + "; HUD: " + hud.getTranslatedKeyMessage().getString()
                            + "; waypoints: " + waypoints.getTranslatedKeyMessage().getString() + ".")); return 1;
                })).then(literal("waypoint").then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                        .<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource, Integer>argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                        .<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource, Integer>argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                        .<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource, Integer>argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                        .executes(context -> {
                            // Clickable coordinates in chat run this (see ChatComponentMixin).
                            String scope = ClientState.scope();
                            var p = ClientState.preferences;
                            if (scope.isEmpty() || p.waypoints.size() >= 32) {
                                context.getSource().sendError(Component.literal("Waypoints are full or unavailable here."));
                                return 0;
                            }
                            int x = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "x");
                            int y = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "y");
                            int z = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "z");
                            var point = new net.aerborne.client.protocol.ClientPreferences.Waypoint(java.util.UUID.randomUUID().toString(),
                                    scope, "Chat " + x + " " + z, x, Math.max(-2048, Math.min(2048, y)), z);
                            p.waypoints.add(point); p.tracked = point.id(); ClientState.save();
                            context.getSource().sendFeedback(Component.literal("Tracking waypoint at " + x + ", " + y + ", " + z + "."));
                            return 1;
                        }))))).then(literal("settings").executes(context -> {
                    pendingAction = () -> context.getSource().getClient().gui.setScreen(new AppearanceScreen(null)); return 1;
                }))));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> org.slf4j.LoggerFactory.getLogger("Aerborne")
                .info("Client controls: menu={}, HUD={}, waypoints={}. Use /aerborneclient status for connection diagnostics.",
                        menu.getTranslatedKeyMessage().getString(), hud.getTranslatedKeyMessage().getString(), waypoints.getTranslatedKeyMessage().getString()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AerborneExtras.tick(client);
            // Open after the chat screen has finished handling/closing for a client command.
            if (pendingAction != null) { Runnable action = pendingAction; pendingAction = null; action.run(); }
            while (menu.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) openMenu(client);
            }
            while (hud.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    if (available()) {
                        ClientState.preferences.hud = !ClientState.preferences.hud; ClientState.save();
                        feedback(client, "Aerborne HUD " + (ClientState.preferences.hud ? "enabled" : "hidden") + ".");
                    } else unavailable(client);
                }
            }
            while (waypoints.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    if (available()) client.gui.setScreen(new WaypointScreen(null));
                    else unavailable(client);
                }
            }
            if (client.player != null && !available() && (client.gui.screen() instanceof AerborneMenuScreen || client.gui.screen() instanceof CharacterScreen
                    || client.gui.screen() instanceof WaypointScreen)) client.gui.setScreen(null);
        });
    }

    private boolean available() { return ClientState.available(); }
    private void openMenu(net.minecraft.client.Minecraft client) {
        if (available()) client.gui.setScreen(new AerborneMenuScreen(this::available));
        else unavailable(client);
    }
    private static void unavailable(net.minecraft.client.Minecraft client) {
        feedback(client, "Aerborne tools work on play.aerborne.net or a server running the Aerborne bridge.");
    }
    private static void feedback(net.minecraft.client.Minecraft client, String message) {
        if (client.player != null) client.player.sendOverlayMessage(Component.literal(message));
    }
}
