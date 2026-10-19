package net.nerdsmp.paper.npc;

import com.google.common.collect.HashMultimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.FactionId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.api.service.ReputationService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.activity.ExcavationService;
import net.nerdsmp.paper.activity.FishingService;
import net.nerdsmp.paper.engagement.QuestAction;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.gui.MenuManager;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.item.PaperItemService;
import net.nerdsmp.paper.item.IdentificationService;
import net.nerdsmp.paper.item.RepairService;
import net.nerdsmp.paper.player.BankService;
import net.nerdsmp.paper.npc.chat.ChatDialogueRenderer;
import net.nerdsmp.paper.npc.chat.ChatDialogueSessions;
import net.nerdsmp.paper.session.SessionRegistry;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class NpcManager implements Listener, AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final SessionRegistry sessions;
    private final EconomyService economy;
    private final ReputationService reputation;
    private final ProgressionService progression;
    private final PaperItemService items;
    private final BankService bank;
    private final IdentificationService identification;
    private final RepairService repairs;
    private final ReputationContractService contracts;
    private final ExcavationService excavation;
    private final FishingService fishing;
    private final NpcProgressStore progress;
    private final NpcShopLedger shopLedger;
    private final NpcVendorTrading vendorTrading;
    private final File instancesFile;
    private final String seasonId;
    private final NamespacedKey npcInstanceKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, NpcInstance> instances = new LinkedHashMap<>();
    private final Map<UUID, LivingEntity> liveByInstance = new HashMap<>();
    private final Map<UUID, TextDisplay> nameplateByInstance = new HashMap<>();
    private final Map<UUID, UUID> instanceByEntity = new HashMap<>();
    private final Set<Chunk> ticketedChunks = new java.util.HashSet<>();
    private final Set<UUID> shopPending = new HashSet<>();
    private final Set<AccountId> dialoguePending = new HashSet<>();
    private final Map<UUID, UUID> conversationTokens = new HashMap<>();
    private final ChatDialogueSessions chatSessions = new ChatDialogueSessions();
    private final Map<UUID, ChatConversation> chatConversations = new HashMap<>();
    private final int maximumInstances;
    private final int assaultLoss;
    private final long assaultCooldownMillis;
    private volatile NpcContent content;
    private BukkitTask gazeTask;

    public NpcManager(
        NerdSmpPlugin plugin,
        SessionRegistry sessions,
        EconomyService economy,
        ReputationService reputation,
        ProgressionService progression,
        PaperItemService items,
        BankService bank,
        IdentificationService identification,
        RepairService repairs,
        ReputationContractService contracts,
        ExcavationService excavation,
        FishingService fishing
    ) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.economy = economy;
        this.reputation = reputation;
        this.progression = progression;
        this.items = items;
        this.bank = bank;
        this.identification = identification;
        this.repairs = repairs;
        this.contracts = contracts;
        this.excavation = excavation;
        this.fishing = fishing;
        seasonId = plugin.getConfig().getString("season.id", "season-1");
        content = new NpcContentLoader(plugin, items).load();
        contracts.validateFactions(content.factions().keySet());
        progress = new NpcProgressStore(plugin, seasonId);
        shopLedger = new NpcShopLedger(plugin, seasonId);
        vendorTrading = plugin.skyblockEnabled() ? new NpcVendorTrading(plugin, () -> content, shopLedger, seasonId) : null;
        instancesFile = new File(plugin.getDataFolder(), "npc-instances.yml");
        npcInstanceKey = new NamespacedKey(plugin, "npc_instance");
        maximumInstances = Math.max(1, Math.min(500,
            plugin.getConfig().getInt("npcs.maximum-instances", 100)));
        assaultLoss = Math.max(1, Math.min(100,
            plugin.getConfig().getInt("npcs.assault-reputation-loss", 10)));
        assaultCooldownMillis = Math.max(5_000L,
            plugin.getConfig().getLong("npcs.assault-cooldown-seconds", 60L) * 1000L);
        loadInstances();
    }

    public void start() {
        respawnAll();
        if (vendorTrading != null) vendorTrading.recover();
        gazeTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateGaze, 10L, 10L);
    }

    public NpcContent content() {
        return content;
    }

    /** Validate an already merged authoring snapshot before committing its source file. */
    public void validateAuthoredContent(NpcContent next) {
        contracts.validateFactions(next.factions().keySet());
        for (NpcInstance instance : instances.values()) {
            var old = content.npcs().get(instance.definitionId());
            var replacement = next.npcs().get(instance.definitionId());
            if (replacement == null) throw new IllegalArgumentException("Cannot remove an NPC with a saved placement: " + instance.definitionId());
            if (old != null && old.entityType() != replacement.entityType())
                throw new IllegalArgumentException("Cannot change the body type of a placed NPC: " + instance.definitionId());
        }
    }

    /** Refresh only manually saved anchors of edited NPCs; publishing never creates a new anchor. */
    public void replaceAuthoredContent(NpcContent next, Set<String> changedIds) {
        validateAuthoredContent(next);
        content = next;
        conversationTokens.clear();
        clearChat();
        for (NpcInstance instance : instances.values()) if (changedIds.contains(instance.definitionId())) {
            removeLive(instance.id());
            try { spawnLive(instance); }
            catch (RuntimeException failure) { plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "NPC content was saved, but a placed body could not refresh: " + instance.definitionId(), failure); }
        }
    }

    public Set<String> definitionIds() {
        return content.npcs().keySet();
    }

    public List<String> missingHubDefinitions() {
        double radius = Math.clamp(plugin.getConfig().getDouble("skyblock.market.hub-radius", 256), 16, 512);
        return content.npcs().keySet().stream().filter(id -> instances.values().stream().noneMatch(instance -> {
            LivingEntity body = liveByInstance.get(instance.id());
            return id.equals(instance.definitionId()) && body != null && body.isValid()
                && plugin.travel().nearCentralSpawn(instance.location(), radius);
        })).sorted().toList();
    }

    public boolean handleNpcCommand(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reply")) {
            Player player = requirePlayer(sender);
            if (player != null) replyCommand(player, args);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("bye")) {
            Player player = requirePlayer(sender);
            if (player != null) leaveCommand(player, args);
            return true;
        }
        if (args.length > 0 && Set.of("shopreview", "shopretry", "shopresolve").contains(args[0].toLowerCase(Locale.ROOT))) {
            if (vendorTrading == null) { Chat.error(sender, "Dynamic vendors are only available in Skyblock mode."); return true; }
            return vendorTrading.command(sender, args);
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("nearby")) {
            Player player = requirePlayer(sender);
            if (player != null) {
                nearby(player);
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("definitions")) {
            sender.sendRichMessage("<gold>NPC definitions:</gold> <white>"
                + String.join(", ", definitionIds()) + "</white>");
            return true;
        }
        if (args[0].equalsIgnoreCase("talk") && args.length == 2) {
            Player player = requirePlayer(sender);
            NpcInstance instance = resolveInstance(args[1]);
            if (player == null || instance == null) {
                sender.sendRichMessage("<red>Use a unique nearby NPC id.</red>");
                return true;
            }
            Location location = instance.location();
            if (!player.getWorld().getUID().equals(instance.worldId())
                || player.getLocation().distanceSquared(location) > 100.0D) {
                sender.sendRichMessage("<red>Move closer to that NPC.</red>");
                return true;
            }
            openDialogue(player, instance, content.npcs().get(instance.definitionId()).rootDialogue());
            return true;
        }
        if (!sender.hasPermission("nerdsmp.npc.admin")) {
            Chat.noPermission(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create", "place" -> createCommand(sender, args);
            case "catalog" -> catalogCommand(sender, args);
            case "remove" -> removeCommand(sender, args);
            case "movehere" -> moveCommand(sender, args);
            case "list" -> listCommand(sender);
            case "respawn" -> {
                respawnAll();
                sender.sendRichMessage("<green>NPC bodies respawned from saved anchors.</green>");
                yield true;
            }
            case "reload" -> {
                NpcContent reloaded = new NpcContentLoader(plugin, items).load();
                contracts.reload();
                contracts.validateFactions(reloaded.factions().keySet());
                content = reloaded;
                conversationTokens.clear();
                clearChat();
                respawnAll();
                sender.sendRichMessage("<green>NPC dialogue, contracts, and bodies reloaded.</green>");
                yield true;
            }
            case "reputation" -> reputationCommand(sender, args);
            default -> false;
        };
    }

    public List<String> tabCompleteNpc(String[] args) {
        if (args.length == 1) {
            return List.of("nearby", "talk", "reply", "bye", "definitions", "catalog", "place", "create", "remove", "movehere", "list",
                "respawn", "reload", "reputation");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("place"))) {
            return definitionIds().stream().sorted().toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("catalog")) {
            return java.util.Arrays.stream(NpcCatalog.Category.values()).map(NpcCatalog.Category::key).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("talk")
            || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("movehere"))) {
            return instances.keySet().stream().map(id -> id.toString().substring(0, 8)).sorted().toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("reputation")) {
            return content.factions().keySet().stream().map(FactionId::value).sorted().toList();
        }
        return List.of();
    }

    public List<String> tabCompleteNpc(CommandSender sender, String[] args) {
        if (sender instanceof Player player && args.length == 2 && args[0].equalsIgnoreCase("reply")) {
            return chatSessions.current(player.getUniqueId()).filter(session -> !session.pending())
                .map(session -> java.util.stream.IntStream.rangeClosed(1, session.choiceIds().size())
                    .mapToObj(Integer::toString).toList()).orElse(List.of());
        }
        if (!sender.hasPermission("nerdsmp.npc.admin")) {
            if (args.length == 1) return List.of("nearby", "talk", "reply", "bye");
            if (args.length == 2 && args[0].equalsIgnoreCase("talk")) return tabCompleteNpc(args);
            return List.of();
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("catalog")) {
            NpcCatalog.Category category = NpcCatalog.Category.parse(args[1]);
            if (category == null) return List.of();
            return new NpcCatalog(content).page(category, "", 0, 45).entries().stream()
                .map(entry -> entry.definition().id()).toList();
        }
        return tabCompleteNpc(args);
    }

    public boolean showReputation(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        AccountId account = account(player);
        if (account == null) {
            return true;
        }
        if (args.length > 0) {
            FactionId faction;
            try {
                faction = new FactionId(args[0].toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                player.sendRichMessage("<red>Unknown faction.</red>");
                return true;
            }
            if (!content.factions().containsKey(faction)) {
                player.sendRichMessage("<red>Unknown faction.</red>");
                return true;
            }
            reputation.value(account, faction).thenAccept(points -> sync(() -> showFaction(player, faction, points)));
            return true;
        }
        Map<FactionId, CompletableFuture<Integer>> futures = new LinkedHashMap<>();
        content.factions().keySet().stream().sorted().forEach(faction ->
            futures.put(faction, reputation.value(account, faction).toCompletableFuture()));
        CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).thenRun(() -> sync(() -> {
            player.sendRichMessage(net.nerdsmp.paper.presentation.BrandPalette.title("Faction standing"));
            futures.forEach((faction, future) -> showFaction(player, faction, future.join()));
            player.sendRichMessage("<gray>Standing changes dialogue, access, and NPC prices.</gray>");
            player.sendRichMessage("<gray>Cato, Lyra, and Garrick offer rotating daily, weekly, and monthly faction work.</gray>");
        }));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        NpcInstance instance = instance(event.getRightClicked());
        if (instance == null) {
            return;
        }
        event.setCancelled(true);
        NpcContent.NpcDefinition definition = content.npcs().get(instance.definitionId());
        if (definition != null) {
            openDialogue(event.getPlayer(), instance, definition.rootDialogue());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        NpcInstance instance = instance(event.getEntity());
        if (instance == null) {
            return;
        }
        event.setCancelled(true);
        Player attacker = attacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        AccountId account = account(attacker);
        NpcContent.NpcDefinition definition = content.npcs().get(instance.definitionId());
        if (account == null || definition == null) {
            return;
        }
        long bucket = System.currentTimeMillis() / assaultCooldownMillis;
        String operation = "npc-assault:" + account + ":" + instance.id() + ":" + bucket;
        reputation.adjust(account, definition.factionId(), -assaultLoss,
            new ReputationService.ReputationReason("npc.assault", "Attacked " + definition.name()),
            operation).thenAccept(result -> sync(() -> {
                if (result.status() == ReputationService.AdjustmentResult.Status.APPLIED) {
                    NpcContent.ReputationView view = content.reputation(definition.factionId(), result.value());
                    attacker.sendRichMessage("<red>" + definition.name() + " is protected.</red> <gray>"
                        + content.faction(definition.factionId()).name() + " standing "
                        + signed(result.appliedAmount()) + " → " + result.value() + " (" + view.tier().name() + ")</gray>");
                }
            }));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof NpcInventory holder)) {
            return;
        }
        boolean alreadyCancelled = event.isCancelled();
        event.setCancelled(true);
        if (alreadyCancelled) return;
        if (!(event.getWhoClicked() instanceof Player player) || !holder.playerId.equals(player.getUniqueId())
            || event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (holder.staffAccount != null && !currentStaffView(player, holder)) return;
        if (holder.browseAccount != null && !currentBrowseView(player, holder)) return;
        if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
            && event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT) return;
        Consumer<Player> action = event.isRightClick() ? holder.secondaryActions.get(event.getRawSlot())
            : holder.actions.get(event.getRawSlot());
        if (action == null && event.isRightClick() && holder.secondaryFallsBack) {
            action = holder.actions.get(event.getRawSlot());
        }
        if (action != null && !holder.actionPending) {
            if (event.getRawSlot() != MenuKit.closeSlot(event.getView().getTopInventory().getSize())) MenuKit.clickSound(player);
            holder.actionPending = true;
            Consumer<Player> selectedAction = action;
            Bukkit.getScheduler().runTask(plugin, () -> {
                holder.actionPending = false;
                if (player.isOnline() && player.getOpenInventory().getTopInventory() == holder.inventory
                    && (holder.staffAccount == null || currentStaffView(player, holder))
                    && (holder.browseAccount == null || currentBrowseView(player, holder))) selectedAction.accept(player);
            });
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof NpcInventory
            && event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof NpcInventory holder && holder.conversationToken != null) {
            conversationTokens.remove(event.getPlayer().getUniqueId(), holder.conversationToken);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        endChat(event.getPlayer().getUniqueId());
        conversationTokens.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        // A station or another menu replaces the conversation; old chat links stay inert.
        endChat(event.getPlayer().getUniqueId());
    }

    private void openDialogue(Player player, NpcInstance instance, String nodeId) {
        NpcContent source = content;
        NpcContent.NpcDefinition npc = source.npcs().get(instance.definitionId());
        NpcContent.DialogueNode node = source.dialogues().get(nodeId);
        AccountId account = account(player);
        if (npc == null || node == null || account == null) {
            return;
        }
        if (dialoguePending.contains(account)) {
            player.sendRichMessage("<yellow>Wait for this response to finish.</yellow>");
            return;
        }
        if (!currentChatLocation(player, account, instance)) {
            player.sendRichMessage("<gray>Move closer to the NPC and keep them in sight.</gray>");
            return;
        }
        player.closeInventory();
        endChat(player.getUniqueId());
        pruneChat();
        UUID token = UUID.randomUUID();
        try {
            chatSessions.start(player.getUniqueId(), account, instance.id(), npc.id(), node.id(),
                node.choices().stream().map(NpcContent.DialogueChoice::id).toList(), token);
        } catch (IllegalStateException busy) {
            player.sendRichMessage("<yellow>Too many conversations are open. Please try again shortly.</yellow>");
            return;
        }
        ChatConversation conversation = new ChatConversation(player, account, instance, source, npc, node, token,
            new AtomicBoolean());
        chatConversations.put(player.getUniqueId(), conversation);
        conversationTokens.put(player.getUniqueId(), token);
        context(player, account, npc).whenComplete((context, error) -> sync(() -> {
            if (!currentChatConversation(player, conversation)) {
                return;
            }
            if (error != null) {
                endChat(player.getUniqueId());
                player.sendRichMessage("<red>This conversation could not be loaded. Please try again.</red>");
                return;
            }
            Instant now = Instant.now();
            List<ChatDialogueRenderer.Choice> responses = new ArrayList<>();
            for (NpcContent.DialogueChoice choice : node.choices()) {
                String key = choiceKey(npc.id(), node.id(), choice.id());
                ChoiceAccess access = access(choice, key, context, now);
                List<String> hints = new ArrayList<>();
                for (NpcContent.DialogueEffect effect : choice.effects()) {
                    String detail = choiceEffectLore(npc, effect);
                    if (detail != null) hints.add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(message(detail)));
                }
                if (choice.once()) hints.add("One-time response");
                else if (!choice.cooldown().isZero()) hints.add("Cooldown: " + shortDuration(choice.cooldown()));
                responses.add(new ChatDialogueRenderer.Choice(resolveDialogue(choice.text(), player, context),
                    access.available(), hints, access.reasons()));
            }
            ChatDialogueRenderer.render(token, npc.name(), npc.title(),
                node.text().stream().map(line -> resolveDialogue(line, player, context)).toList(), responses)
                .forEach(player::sendMessage);
            conversation.rendered().set(true);
        }));
    }

    private void replyCommand(Player player, String[] args) {
        if (args.length != 2 && args.length != 3) {
            player.sendRichMessage("<gray>Click a numbered reply, or use /npc reply 1.</gray>");
            return;
        }
        ChatConversation conversation = chatConversations.get(player.getUniqueId());
        UUID token;
        int number;
        try {
            token = args.length == 3 ? UUID.fromString(args[1]) : conversation == null ? null : conversation.token();
            number = Integer.parseInt(args[args.length - 1]);
        } catch (IllegalArgumentException invalid) {
            player.sendRichMessage("<gray>Click a numbered reply, or use /npc reply 1.</gray>");
            return;
        }
        if (conversation == null || token == null || !token.equals(conversation.token())
            || !currentChatConversation(player, conversation)) {
            // An old click must not close a newer, valid conversation.
            player.sendRichMessage("<gray>That reply has expired. Talk to the NPC again.</gray>");
            return;
        }
        if (!conversation.rendered().get()) {
            player.sendRichMessage("<yellow>The NPC is still getting ready to speak.</yellow>");
            return;
        }
        if (dialoguePending.contains(conversation.account())) {
            player.sendRichMessage("<yellow>Wait for this response to finish.</yellow>");
            return;
        }
        ChatDialogueSessions.Claim claim = chatSessions.begin(player.getUniqueId(), conversation.account(), token, number);
        if (!claim.accepted()) {
            player.sendRichMessage(claim.status() == ChatDialogueSessions.Status.BUSY
                ? "<yellow>Wait for this response to finish.</yellow>"
                : "<gray>Choose one of the numbered replies shown in your conversation.</gray>");
            return;
        }
        selectChoice(player, conversation, conversation.node().choices().get(number - 1));
    }

    private void leaveCommand(Player player, String[] args) {
        ChatConversation conversation = chatConversations.get(player.getUniqueId());
        if (args.length > 2) {
            player.sendRichMessage("<gray>Use /npc bye to leave a conversation.</gray>");
            return;
        }
        if (args.length == 2) {
            try {
                if (conversation == null || !UUID.fromString(args[1]).equals(conversation.token())) {
                    player.sendRichMessage("<gray>That conversation has already ended.</gray>");
                    return;
                }
            } catch (IllegalArgumentException invalid) { return; }
        }
        endChat(player.getUniqueId());
        player.sendRichMessage("<gray>Conversation ended.</gray>");
    }

    private boolean currentChatConversation(Player player, ChatConversation conversation) {
        return conversation.player() == player && chatConversations.get(player.getUniqueId()) == conversation
            && content == conversation.source()
            && conversation.token().equals(conversationTokens.get(player.getUniqueId()))
            && chatSessions.matches(player.getUniqueId(), conversation.account(), conversation.token())
            && currentChatLocation(player, conversation.account(), conversation.instance());
    }

    private boolean currentChatLocation(Player player, AccountId account, NpcInstance instance) {
        LivingEntity body = liveByInstance.get(instance.id());
        return currentNpcContext(player, account, instance) && !player.isDead()
            && body != null && body.isValid() && !body.isDead() && player.hasLineOfSight(body);
    }

    private void endChat(UUID playerId) {
        ChatConversation previous = chatConversations.remove(playerId);
        chatSessions.invalidate(playerId);
        if (previous != null) conversationTokens.remove(playerId, previous.token());
    }

    private void clearChat() {
        for (UUID playerId : List.copyOf(chatConversations.keySet())) endChat(playerId);
        chatSessions.clear();
    }

    private void pruneChat() {
        for (ChatConversation conversation : List.copyOf(chatConversations.values())) {
            if (!currentChatConversation(conversation.player(), conversation)) endChat(conversation.player().getUniqueId());
        }
    }

    private CompletableFuture<DialogueContext> context(
        Player player,
        AccountId account,
        NpcContent.NpcDefinition npc
    ) {
        try {
            CompletableFuture<Integer> standing = reputation.value(account, npc.factionId()).toCompletableFuture();
            CompletableFuture<Long> balance = economy.balance(account).toCompletableFuture();
            CompletableFuture<ProgressionService.ProgressionSnapshot> levels = progression.snapshot(account).toCompletableFuture();
            return CompletableFuture.allOf(standing, balance, levels).thenApply(ignored -> new DialogueContext(
                account, standing.join(), balance.join(), levels.join().adventurer().level(), progress.snapshot(account)
            ));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    static ChoiceAccess access(
        NpcContent.DialogueChoice choice,
        String choiceKey,
        DialogueContext context,
        Instant now
    ) {
        List<String> reasons = new ArrayList<>();
        NpcContent.Requirements required = choice.requirements();
        if (context.reputation() < required.minimumReputation()) {
            reasons.add("Requires " + required.minimumReputation() + " reputation");
        }
        if (context.reputation() > required.maximumReputation()) {
            reasons.add("Only available below " + required.maximumReputation() + " reputation");
        }
        if (context.balance() < required.minimumMarks()) {
            reasons.add("Requires " + Chat.marks(required.minimumMarks()));
        }
        if (context.adventurerLevel() < required.minimumAdventurerLevel()) {
            reasons.add("Requires Adventurer Level " + required.minimumAdventurerLevel());
        }
        required.requiredFlags().stream().filter(flag -> !context.progress().flag(flag))
            .forEach(flag -> reasons.add("Story requirement not met"));
        required.absentFlags().stream().filter(context.progress()::flag)
            .forEach(flag -> reasons.add("Already completed"));
        if (choice.once() && context.progress().completedChoices().contains(choiceKey)) {
            reasons.add("Already chosen");
        }
        Instant last = context.progress().lastUsed(choiceKey);
        if (last != null && !choice.cooldown().isZero() && last.plus(choice.cooldown()).isAfter(now)) {
            Duration left = Duration.between(now, last.plus(choice.cooldown()));
            long minutes = Math.max(1L, (left.toSeconds() + 59L) / 60L);
            reasons.add("Available again in " + minutes + "m");
        }
        return new ChoiceAccess(reasons.isEmpty(), reasons);
    }

    static int[] dialogueChoiceSlots(int count) {
        return switch (count) {
            case 0 -> new int[0];
            case 1 -> new int[]{31};
            case 2 -> new int[]{30, 32};
            case 3 -> new int[]{29, 31, 33};
            case 4 -> new int[]{28, 30, 32, 34};
            case 5 -> new int[]{28, 30, 32, 34, 40};
            default -> throw new IllegalArgumentException("Dialogue supports at most five choices");
        };
    }

    private List<String> dialogueLore(
        NpcContent.DialogueNode node,
        Player player,
        DialogueContext context
    ) {
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>----------------------------</dark_gray>");
        for (int lineIndex = 0; lineIndex < node.text().size(); lineIndex++) {
            String resolved = resolveDialogue(node.text().get(lineIndex), player, context);
            List<String> wrapped = wrap(resolved, 44);
            for (int index = 0; index < wrapped.size(); index++) {
                String prefix = index == 0 ? "\"" : "  ";
                String suffix = index == wrapped.size() - 1 ? "\"" : "";
                lore.add("<white>" + escape(prefix + wrapped.get(index) + suffix) + "</white>");
            }
            if (lineIndex < node.text().size() - 1) {
                lore.add("<gray> </gray>");
            }
        }
        lore.add("<dark_gray>----------------------------</dark_gray>");
        lore.add("<gray>Choose a response below.</gray>");
        return List.copyOf(lore);
    }

    private List<String> choiceLore(
        NpcContent.NpcDefinition npc,
        NpcContent.DialogueChoice choice,
        ChoiceAccess access
    ) {
        List<String> lore = new ArrayList<>();
        if (!access.available()) {
            lore.add("<red><bold>LOCKED</bold></red>");
            lore.add("<dark_gray>--------------------</dark_gray>");
            access.reasons().forEach(reason -> lore.add("<gray>- " + escape(reason) + "</gray>"));
            return List.copyOf(lore);
        }
        lore.add("<green><bold>AVAILABLE</bold></green>");
        for (NpcContent.DialogueEffect effect : choice.effects()) {
            String detail = choiceEffectLore(npc, effect);
            if (detail != null) {
                lore.add(detail);
            }
        }
        if (choice.once()) {
            lore.add("<dark_gray>One-time response</dark_gray>");
        } else if (!choice.cooldown().isZero()) {
            lore.add("<dark_gray>Cooldown: " + shortDuration(choice.cooldown()) + "</dark_gray>");
        }
        if (choice.effects().isEmpty() && choice.nextNode() != null) {
            lore.add("<gray>Continue the conversation.</gray>");
        }
        lore.add("<dark_gray>--------------------</dark_gray>");
        lore.add("<yellow>Click to choose.</yellow>");
        return List.copyOf(lore);
    }

    private String choiceEffectLore(NpcContent.NpcDefinition npc, NpcContent.DialogueEffect effect) {
        return switch (effect.type()) {
            case REPUTATION -> {
                FactionId faction = effect.factionId() == null ? npc.factionId() : effect.factionId();
                yield "<gray>Standing:</gray> <aqua>" + signed(effect.amount()) + " "
                    + escape(content.faction(faction).name()) + "</aqua>";
            }
            case MARKS -> effect.amount() > 0
                ? "<gray>Reward</gray> <gold>+" + Chat.marks(effect.amount()) + "</gold>"
                : "<gray>Cost</gray> " + Chat.marksTag(Math.abs((long) effect.amount()));
            case OPEN_SHOP -> "<gray>Opens:</gray> <white>" + escape(content.shops().get(effect.shopId()).title())
                + "</white>";
            case OPEN_BANK -> "<gray>Opens:</gray> <white>Bank vault</white>";
            case OPEN_IDENTIFIER -> "<gray>Opens:</gray> <white>Item identification</white>";
            case OPEN_REPAIR -> "<gray>Opens:</gray> <white>Repair service</white>";
            case OPEN_CONTRACTS -> "<gray>Opens:</gray> <white>Faction contracts</white>";
            case OPEN_ARCHIVE -> "<gray>Opens:</gray> <white>Field Archive appraisal</white>";
            case OPEN_FISHERY -> "<gray>Opens:</gray> <white>Ivo's catch appraisal</white>";
            case OPEN_ALCHEMY -> "<gray>Opens:</gray> <white>Alchemy workbench</white>";
            case OPEN_FORGE -> "<gray>Opens:</gray> <white>Arcane forge</white>";
            case OPEN_ENCHANTMENTS -> "<gray>Opens:</gray> <white>Arcane enchanting</white>";
            case OPEN_COMMAND -> "<gray>Opens:</gray> <white>/" + escape(effect.command()) + "</white>";
            case FLAG -> null;
        };
    }

    private Material choiceMaterial(NpcContent.DialogueChoice choice, boolean available) {
        if (!available) {
            return Material.GRAY_DYE;
        }
        for (NpcContent.DialogueEffect effect : choice.effects()) {
            Material service = switch (effect.type()) {
                case OPEN_SHOP -> Material.CHEST;
                case OPEN_BANK -> Material.ENDER_CHEST;
                case OPEN_IDENTIFIER -> Material.SPYGLASS;
                case OPEN_REPAIR -> Material.ANVIL;
                case OPEN_CONTRACTS -> Material.LECTERN;
                case OPEN_ARCHIVE -> Material.DECORATED_POT;
                case OPEN_FISHERY -> Material.BARREL;
                case OPEN_ALCHEMY -> Material.BREWING_STAND;
                case OPEN_FORGE -> Material.SMITHING_TABLE;
                case OPEN_ENCHANTMENTS -> Material.ENCHANTING_TABLE;
                case OPEN_COMMAND -> Material.COMPASS;
                default -> null;
            };
            if (service != null) {
                return service;
            }
        }
        if (choice.close()) {
            return Material.OAK_DOOR;
        }
        if (choice.effects().stream().anyMatch(effect -> effect.type() == NpcContent.EffectType.REPUTATION)) {
            return Material.EMERALD;
        }
        return choice.nextNode() == null ? Material.PAPER : Material.WRITABLE_BOOK;
    }

    private void selectChoice(
        Player player,
        ChatConversation conversation,
        NpcContent.DialogueChoice choice
    ) {
        NpcContent.NpcDefinition npc = conversation.npc();
        NpcContent.DialogueNode node = conversation.node();
        NpcInstance instance = conversation.instance();
        AccountId account = conversation.account();
        if (!currentChatConversation(player, conversation)) {
            return;
        }
        if (!dialoguePending.add(account)) {
            player.sendRichMessage("<yellow>Wait for this response to finish.</yellow>");
            return;
        }
        context(player, account, npc).whenComplete((context, readError) -> sync(() -> {
            if (!currentChatConversation(player, conversation)) {
                dialoguePending.remove(account);
                return;
            }
            if (readError != null) {
                dialoguePending.remove(account);
                endChat(player.getUniqueId());
                player.sendRichMessage("<red>Your conversation progress could not be loaded. Please try again.</red>");
                return;
            }
            Instant now = Instant.now();
            String choiceKey = choiceKey(npc.id(), node.id(), choice.id());
            ChoiceAccess currentAccess = access(choice, choiceKey, context, now);
            if (!currentAccess.available()) {
                dialoguePending.remove(account);
                player.sendRichMessage("<red>That response is no longer available.</red>");
                openDialogue(player, instance, node.id());
                return;
            }
            CompletionStage<ChoiceOutcome> action;
            try {
                action = applyChoice(player, npc, node, choice, context, now);
            } catch (RuntimeException exception) {
                action = CompletableFuture.failedFuture(exception);
            }
            action.whenComplete((outcome, error) -> sync(() -> {
                dialoguePending.remove(account);
                if (!currentChatConversation(player, conversation)) {
                    if (error != null) {
                        plugin.getLogger().warning("NPC choice failed after conversation ended for " + account
                            + ": " + rootMessage(error));
                    }
                    return;
                }
                if (error != null) {
                    String message = rootMessage(error);
                    player.sendRichMessage("<red>Conversation action failed:</red> <gray>" + escape(message) + "</gray>");
                    openDialogue(player, instance, node.id());
                    return;
                }
                // Complete this rendering before a menu, command or new branch can take over.
                endChat(player.getUniqueId());
                if (outcome.service() == ServiceAction.BANK) {
                    bank.open(player);
                } else if (outcome.service() == ServiceAction.IDENTIFIER) {
                    identification.open(player);
                } else if (outcome.service() == ServiceAction.REPAIR) {
                    repairs.open(player);
                } else if (outcome.service() == ServiceAction.CONTRACTS) {
                    openContracts(player, instance);
                } else if (outcome.service() == ServiceAction.ARCHIVE) {
                    excavation.openArchive(player);
                } else if (outcome.service() == ServiceAction.FISHERY) {
                    fishing.openFishery(player);
                } else if (outcome.service() == ServiceAction.ALCHEMY) {
                    plugin.alchemy().open(player);
                } else if (outcome.service() == ServiceAction.FORGE) {
                    plugin.menus().openStation(player, net.nerdsmp.paper.gui.MenuManager.View.FORGE);
                } else if (outcome.service() == ServiceAction.ENCHANTMENTS) {
                    plugin.menus().openStation(player, net.nerdsmp.paper.gui.MenuManager.View.ENCHANTMENTS);
                } else if (outcome.shopId() != null) {
                    openShop(player, instance, outcome.shopId(), node.id());
                } else if (outcome.command() != null) {
                    player.closeInventory();
                    if (!player.performCommand(outcome.command())) {
                        player.sendRichMessage("<red>That service is currently unavailable.</red>");
                    }
                } else if (choice.nextNode() != null) {
                    openDialogue(player, instance, choice.nextNode());
                } else {
                    player.sendRichMessage("<gray>Conversation ended.</gray>");
                }
            }));
        }));
    }

    private NpcViewRequest requestView(Player player, AccountId account, NpcInstance instance) {
        endChat(player.getUniqueId());
        UUID token = UUID.randomUUID();
        conversationTokens.put(player.getUniqueId(), token);
        return new NpcViewRequest(token, account, instance, player.getOpenInventory().getTopInventory());
    }

    private boolean currentRequest(Player player, NpcViewRequest request) {
        return request.token().equals(conversationTokens.get(player.getUniqueId()))
            && player.getOpenInventory().getTopInventory() == request.source()
            && currentNpcContext(player, request.account(), request.instance());
    }

    private boolean currentConversation(Player player, AccountId account, NpcInstance instance, NpcInventory holder) {
        return holder.conversationToken != null
            && holder.conversationToken.equals(conversationTokens.get(player.getUniqueId()))
            && player.getOpenInventory().getTopInventory() == holder.inventory
            && currentNpcContext(player, account, instance);
    }

    private boolean currentNpcContext(Player player, AccountId account, NpcInstance instance) {
        return player.isOnline() && plugin.isEnabled()
            && Bukkit.getPlayer(player.getUniqueId()) == player
            && sessions.account(player.getUniqueId()).filter(account::equals).isPresent()
            && instance.equals(instances.get(instance.id()))
            && player.getWorld().getUID().equals(instance.worldId())
            && player.getLocation().distanceSquared(instance.location()) <= 100.0D;
    }

    private CompletionStage<ChoiceOutcome> applyChoice(
        Player player,
        NpcContent.NpcDefinition npc,
        NpcContent.DialogueNode node,
        NpcContent.DialogueChoice choice,
        DialogueContext context,
        Instant now
    ) {
        String choiceKey = choiceKey(npc.id(), node.id(), choice.id());
        String token;
        if (choice.once()) {
            token = "once";
        } else if (!choice.cooldown().isZero()) {
            token = Long.toString(now.toEpochMilli() / choice.cooldown().toMillis());
        } else {
            token = UUID.randomUUID().toString();
        }
        UUID operation = UUID.nameUUIDFromBytes(
            (context.account() + ":" + choiceKey + ":" + token).getBytes(StandardCharsets.UTF_8)
        );
        long marks = choice.effects().stream()
            .filter(effect -> effect.type() == NpcContent.EffectType.MARKS)
            .mapToLong(NpcContent.DialogueEffect::amount).sum();
        AtomicBoolean debited = new AtomicBoolean();
        CompletionStage<Void> stage = CompletableFuture.completedFuture(null);
        if (marks < 0L) {
            stage = economy.debit(context.account(), -marks,
                new EconomyService.TransactionReason("npc.dialogue", npc.name() + ": " + choice.text()),
                "npc-dialogue:" + operation + ":debit").thenAccept(result -> {
                    if (result.status() == EconomyService.TransactionResult.Status.INSUFFICIENT_FUNDS) {
                        throw new CompletionException(new IllegalStateException("You do not have enough Marks."));
                    }
                    if (result.status() != EconomyService.TransactionResult.Status.APPLIED
                        && result.status() != EconomyService.TransactionResult.Status.DUPLICATE) {
                        throw new CompletionException(new IllegalStateException("Marks payment was rejected."));
                    }
                    debited.set(result.status() == EconomyService.TransactionResult.Status.APPLIED);
                });
        }
        int effectIndex = 0;
        for (NpcContent.DialogueEffect effect : choice.effects()) {
            if (effect.type() != NpcContent.EffectType.REPUTATION) {
                continue;
            }
            int index = effectIndex++;
            FactionId faction = effect.factionId() == null ? npc.factionId() : effect.factionId();
            stage = stage.thenCompose(ignored -> reputation.adjust(
                context.account(), faction, effect.amount(),
                new ReputationService.ReputationReason("npc.dialogue", npc.name() + ": " + choice.text()),
                "npc-dialogue:" + operation + ":reputation:" + index
            )).thenAccept(result -> {
                if (result.status() == ReputationService.AdjustmentResult.Status.INVALID_AMOUNT) {
                    throw new CompletionException(new IllegalStateException("Reputation adjustment was rejected."));
                }
                if (result.status() == ReputationService.AdjustmentResult.Status.APPLIED) {
                    sync(() -> {
                        if (player.isOnline()
                            && sessions.account(player.getUniqueId()).filter(context.account()::equals).isPresent()) {
                            player.sendRichMessage("<aqua>" + escape(content.faction(faction).name())
                                + "</aqua> <gray>standing " + signed(result.appliedAmount()) + " → " + result.value() + "</gray>");
                        }
                    });
                }
            });
        }
        if (marks > 0L) {
            stage = stage.thenCompose(ignored -> economy.credit(context.account(), marks,
                new EconomyService.TransactionReason("npc.dialogue", npc.name() + ": " + choice.text()),
                "npc-dialogue:" + operation + ":credit")).thenAccept(result -> {
                    if (result.status() != EconomyService.TransactionResult.Status.APPLIED
                        && result.status() != EconomyService.TransactionResult.Status.DUPLICATE) {
                        throw new CompletionException(new IllegalStateException("Marks reward was rejected."));
                    }
                });
        }
        Map<String, Boolean> flags = new HashMap<>();
        String shopId = null;
        String command = null;
        ServiceAction service = ServiceAction.NONE;
        for (NpcContent.DialogueEffect effect : choice.effects()) {
            if (effect.type() == NpcContent.EffectType.FLAG) {
                flags.put(effect.key(), effect.flagValue());
            } else if (effect.type() == NpcContent.EffectType.OPEN_SHOP) {
                shopId = effect.shopId();
            } else if (effect.type() == NpcContent.EffectType.OPEN_BANK) {
                service = ServiceAction.BANK;
            } else if (effect.type() == NpcContent.EffectType.OPEN_IDENTIFIER) {
                service = ServiceAction.IDENTIFIER;
            } else if (effect.type() == NpcContent.EffectType.OPEN_REPAIR) {
                service = ServiceAction.REPAIR;
            } else if (effect.type() == NpcContent.EffectType.OPEN_CONTRACTS) {
                service = ServiceAction.CONTRACTS;
            } else if (effect.type() == NpcContent.EffectType.OPEN_ARCHIVE) {
                service = ServiceAction.ARCHIVE;
            } else if (effect.type() == NpcContent.EffectType.OPEN_FISHERY) {
                service = ServiceAction.FISHERY;
            } else if (effect.type() == NpcContent.EffectType.OPEN_ALCHEMY) {
                service = ServiceAction.ALCHEMY;
            } else if (effect.type() == NpcContent.EffectType.OPEN_FORGE) {
                service = ServiceAction.FORGE;
            } else if (effect.type() == NpcContent.EffectType.OPEN_ENCHANTMENTS) {
                service = ServiceAction.ENCHANTMENTS;
            } else if (effect.type() == NpcContent.EffectType.OPEN_COMMAND) {
                command = effect.command();
            }
        }
        String targetShop = shopId;
        String targetCommand = command;
        ServiceAction targetService = service;
        CompletionStage<ChoiceOutcome> outcome = stage.thenApply(ignored -> {
            progress.recordChoice(context.account(), choiceKey, choice.once(), choice.cooldown(), now, flags);
            return new ChoiceOutcome(targetShop, targetService, targetCommand);
        });
        return outcome.exceptionallyCompose(error -> {
            if (!debited.get()) {
                return CompletableFuture.failedFuture(error);
            }
            return economy.credit(context.account(), -marks,
                new EconomyService.TransactionReason("npc.dialogue.refund", "Dialogue action failed"),
                "npc-dialogue:" + operation + ":refund"
            ).thenCompose(ignored -> CompletableFuture.failedFuture(error));
        });
    }

    private void openContracts(Player player, NpcInstance instance) {
        NpcContent.NpcDefinition npc = content.npcs().get(instance.definitionId());
        AccountId account = account(player);
        if (npc == null || account == null) {
            return;
        }
        NpcViewRequest request = requestView(player, account, instance);
        context(player, account, npc).thenAccept(context -> sync(() -> {
            if (!currentRequest(player, request)) {
                return;
            }
            Instant now = Instant.now();
            List<ReputationContractService.ContractView> views = contracts.views(
                account, npc.factionId(), context.adventurerLevel(), now
            );
            NpcInventory holder = new NpcInventory(player.getUniqueId());
            holder.conversationToken = request.token();
            Inventory inventory = Bukkit.createInventory(holder, 45,
                message("<aqua><bold>Faction Contracts</bold></aqua> <dark_gray>|</dark_gray> <gold>"
                    + escape(npc.name()) + "</gold>"));
            holder.inventory = inventory;
            decorate(inventory);
            inventory.setItem(4, icon(Material.LECTERN, "<gold><bold>Contract Board</bold></gold>", List.of(
                "<gray>Accept work before progress can count.</gray>",
                "<gray>Return here when an assignment is complete.</gray>",
                "<dark_gray>Offers rotate at their UTC reset.</dark_gray>"
            )));
            inventory.setItem(8, reputationIcon(npc.factionId(), context.reputation()));
            int[] slots = {19, 21, 23, 25};
            for (int index = 0; index < views.size() && index < slots.length; index++) {
                ReputationContractService.ContractView view = views.get(index);
                int slot = slots[index];
                inventory.setItem(slot, contractIcon(view, now));
                holder.actions.put(slot, target -> selectContract(target, instance, npc, context, view));
            }
            inventory.setItem(MenuKit.backSlot(45), MenuKit.back(escape(npc.name())));
            holder.actions.put(MenuKit.backSlot(45), target -> openDialogue(target, instance, npc.rootDialogue()));
            inventory.setItem(MenuKit.closeSlot(45), MenuKit.close());
            holder.actions.put(MenuKit.closeSlot(45), Player::closeInventory);
            player.openInventory(inventory);
        }));
    }

    private void selectContract(
        Player player,
        NpcInstance instance,
        NpcContent.NpcDefinition npc,
        DialogueContext context,
        ReputationContractService.ContractView view
    ) {
        ReputationContract contract = view.contract();
        Instant now = Instant.now();
        if (view.status() == ReputationContractService.ContractStatus.AVAILABLE) {
            ReputationContractService.AcceptResult result = contracts.accept(
                context.account(), npc.factionId(), contract.id(), context.adventurerLevel(), now
            );
            if (result == ReputationContractService.AcceptResult.ACCEPTED) {
                player.sendRichMessage("<green>Contract accepted:</green> <white>" + escape(contract.title())
                    + "</white> <gray>" + actionLabel(contract.action()) + " 0/" + contract.target() + ".</gray>");
            } else if (result == ReputationContractService.AcceptResult.LOCKED) {
                player.sendRichMessage("<red>That contract requires Adventurer Level "
                    + contract.minimumAdventurerLevel() + ".</red>");
            } else if (result == ReputationContractService.AcceptResult.PREREQUISITE_LOCKED) {
                player.sendRichMessage("<red>Finish the previous story chapter first.</red>");
            } else {
                player.sendRichMessage("<yellow>That contract is already active or has rotated.</yellow>");
            }
            openContracts(player, instance);
            return;
        }
        if (view.status() == ReputationContractService.ContractStatus.READY) {
            contracts.claim(context.account(), npc.factionId(), contract.id(), now)
                .whenComplete((result, error) -> sync(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        player.sendRichMessage("<red>Contract claim failed:</red> <gray>"
                            + escape(rootMessage(error)) + "</gray>");
                    } else if (result == ReputationContractService.ClaimResult.CLAIMED) {
                        player.sendRichMessage("<green>Contract claimed:</green> <white>" + escape(contract.title())
                            + "</white> <aqua>+" + contract.reputationReward() + " reputation</aqua>"
                            + (contract.markReward() > 0L ? " <gold>+" + Chat.marks(contract.markReward()) + "</gold>" : ""));
                    } else if (result == ReputationContractService.ClaimResult.PENDING) {
                        player.sendRichMessage("<yellow>That reward is already being processed.</yellow>");
                    } else {
                        player.sendRichMessage("<yellow>That contract is no longer ready to claim.</yellow>");
                    }
                    openContracts(player, instance);
                }));
            return;
        }
        if (view.status() == ReputationContractService.ContractStatus.ACTIVE) {
            player.sendActionBar(message("<aqua>" + escape(contract.title()) + "</aqua> <gray>"
                + view.progress() + "/" + contract.target() + "</gray>"));
        } else if (view.status() == ReputationContractService.ContractStatus.CLAIMED) {
            player.sendActionBar(message("<gray>Already claimed. New work arrives after the reset.</gray>"));
        } else {
            player.sendActionBar(message("<red>Requires Adventurer Level "
                + contract.minimumAdventurerLevel() + ".</red>"));
        }
    }

    private void openShop(Player player, NpcInstance instance, String shopId, String returnNodeId) {
        if (vendorTrading != null) {
            AccountId account = account(player);
            if (account == null || !currentNpcContext(player, account, instance)) return;
            var merchant = content.npcs().get(instance.definitionId());
            var shop = content.shops().get(shopId);
            if (!ShopMerchantResolver.availableShop(content, merchant, shop)) return;
            openTradingShop(player, new ShopAccess(content, merchant, shop, instance, returnNodeId, "", 0), 0);
            return;
        }
        openShop(player, instance, shopId, returnNodeId, 0);
    }

    private void openRemoteShop(Player player, String shopId, String query, int directoryPage) {
        if (vendorTrading == null) { openStockGuide(player, shopId, query, directoryPage, 0); return; }
        var merchant = ShopMerchantResolver.resolve(content, shopId);
        var shop = content.shops().get(shopId);
        if (merchant == null || shop == null) {
            Chat.error(player, "This shop is not available. Please contact staff.");
            return;
        }
        openTradingShop(player, new ShopAccess(content, merchant, shop, null, null, query, directoryPage), 0);
    }

    private boolean currentShopAccess(Player player, AccountId account, ShopAccess access) {
        return plugin.isEnabled() && player.isOnline() && !player.isDead()
            && Bukkit.getPlayer(player.getUniqueId()) == player
            && sessions.account(player.getUniqueId()).filter(account::equals).isPresent()
            && access.source() == content
            && ShopMerchantResolver.availableShop(content, access.merchant(), access.shop());
    }

    private ShopViewRequest requestShopView(Player player, AccountId account, ShopAccess access) {
        endChat(player.getUniqueId());
        UUID token = UUID.randomUUID();
        conversationTokens.put(player.getUniqueId(), token);
        return new ShopViewRequest(token, account, access, player.getOpenInventory().getTopInventory());
    }

    private boolean currentShopRequest(Player player, ShopViewRequest request) {
        return request.token().equals(conversationTokens.get(player.getUniqueId()))
            && player.getOpenInventory().getTopInventory() == request.source()
            && currentShopAccess(player, request.account(), request.access());
    }

    private boolean currentShopView(Player player, AccountId account, ShopAccess access, NpcInventory holder) {
        return holder.conversationToken != null
            && holder.conversationToken.equals(conversationTokens.get(player.getUniqueId()))
            && holder.shopAccess == access && account.equals(holder.browseAccount)
            && player.getOpenInventory().getTopInventory() == holder.inventory
            && currentShopAccess(player, account, access);
    }

    private NpcInventory shopInventory(Player player, ShopViewRequest request, String title) {
        NpcInventory holder = browseInventory(player, title);
        holder.conversationToken = request.token();
        holder.shopAccess = request.access();
        return holder;
    }

    private boolean canTrade(Player player) {
        return !player.isDead() && player.getGameMode() == org.bukkit.GameMode.SURVIVAL;
    }

    private void shopUnavailable(Player player, Throwable failure) {
        plugin.getLogger().log(java.util.logging.Level.WARNING, "Shop view could not load", failure);
        if (player.isOnline()) Chat.error(player, "The shop is unavailable right now. Please try again.");
    }

    private int matchingStock(Player player, NpcContent.ShopProduct product) {
        ItemStack template = ShopItems.create(items, product, 1);
        long count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.isSimilar(template) && item.getAmount() > 0
                && item.getAmount() <= item.getMaxStackSize()) count += item.getAmount();
        }
        return (int)Math.min(Integer.MAX_VALUE, count);
    }

    private List<NpcContent.ShopProduct> filteredStock(ShopAccess access) {
        String query = access.query().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').strip();
        if (query.isBlank()) return access.shop().products();
        List<String> terms = List.of(query.split("\\s+"));
        var found = access.shop().products().stream().filter(product -> {
            String text = (product.id() + " " + product.description() + " "
                + (product.material() == null ? product.customItem().value() : product.material().name()))
                .toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
            return terms.stream().allMatch(text::contains);
        }).toList();
        return found.isEmpty() ? access.shop().products() : found;
    }

    private void backFromShop(Player player, ShopAccess access) {
        AccountId account = account(player);
        if (access.returnInstance() != null && account != null
            && currentNpcContext(player, account, access.returnInstance())) {
            openDialogue(player, access.returnInstance(), access.returnNode());
        } else openShopDirectory(player, access.query(), access.directoryPage());
    }

    private void openTradingShop(Player player, ShopAccess access, int requestedPage) {
        AccountId account = account(player);
        if (account == null || !currentShopAccess(player, account, access)) return;
        ShopViewRequest request = requestShopView(player, account, access);
        reputation.value(account, access.merchant().factionId()).thenCombine(economy.balance(account),
            (points, balance) -> new long[]{points, balance}).whenComplete((values, error) -> sync(() -> {
            if (!currentShopRequest(player, request)) return;
            if (error != null) { shopUnavailable(player, error); return; }
            try {
                var shop = access.shop();
                int points = (int)values[0];
                var standing = content.reputation(access.merchant().factionId(), points);
                NpcInventory holder = shopInventory(player, request, "<gold>" + escape(shop.title()) + "</gold>");
                Inventory inventory = holder.inventory;
                List<String> header = new ArrayList<>();
                wrap(shop.description(), 42).forEach(line -> header.add("<gray>" + escape(line) + "</gray>"));
                header.add("<gray>Merchant:</gray> <white>" + escape(access.merchant().name()) + "</white>");
                header.add("<gray>Balance</gray> " + Chat.marksTag(values[1]));
                header.add("<gray>Left-click an item to buy, right-click to sell.</gray>");
                header.add("<gray>Review the total, then confirm.</gray>");
                if (!canTrade(player)) header.add("<red>Switch to Survival to trade.</red>");
                inventory.setItem(4, icon(Material.CHEST, "<gold>" + escape(shop.title()) + "</gold>", header));
                inventory.setItem(8, reputationIcon(access.merchant().factionId(), points));
                var stock = filteredStock(access);
                var page = ShopLayout.page(stock, requestedPage);
                for (int index = 0; index < page.entries().size(); index++) {
                    var product = page.entries().get(index);
                    boolean unlocked = points >= product.minimumReputation() && standing.tier().canTrade();
                    var ask = unlocked && product.basePrice() > 0 ? vendorTrading.quote(product, standing, NpcMarket.Side.BUY) : null;
                    var bid = unlocked && product.sellPrice() > 0 ? vendorTrading.quote(product, standing, NpcMarket.Side.SELL) : null;
                    List<String> lore = new ArrayList<>();
                    wrap(product.description(), 42).forEach(line -> lore.add("<gray>" + escape(line) + "</gray>"));
                    lore.add("<dark_gray>------------------------</dark_gray>");
                    lore.add("<gray>Bundle:</gray> <white>" + product.amount() + " items</white>");
                    if (unlocked) {
                        lore.add(ask == null ? "<gray>Not sold here.</gray>"
                            : "<gray>Buy</gray> " + Chat.marksTag(ask.marks()));
                        lore.add(bid == null || bid.marks() == 0 ? "<gray>No buyback offer.</gray>"
                            : "<gray>Sell</gray> " + Chat.marksTag(bid.marks()));
                        lore.add("<gray>In your bag:</gray> <white>" + matchingStock(player, product) + " matching items</white>");
                        if (product.sellPrice() > 0) lore.add("<gray>Sales left today:</gray> <white>"
                            + shopLedger.remaining(account, shop.id(), product.id(), product.dailySellLimit())
                            + "/" + product.dailySellLimit() + " bundles</white>");
                        lore.add("<gray>" + escape(vendorTrading.trend(product)) + "</gray>");
                        lore.add("");
                        lore.add(MenuKit.hint("review a purchase"));
                        lore.add("<gray>Right-click to review a sale.</gray>");
                    } else lore.add("<red>Requires " + product.minimumReputation() + " reputation and trading access.</red>");
                    int slot = ShopLayout.slot(index);
                    inventory.setItem(slot, productIcon(product, unlocked, lore));
                    if (unlocked) {
                        holder.actions.put(slot, target -> openTradingItem(target, access, product.id(), page.number(),
                            product.amount(), ask == null ? null : NpcMarket.Side.BUY));
                        holder.secondaryActions.put(slot, target -> openTradingItem(target, access, product.id(), page.number(),
                            product.amount(), bid == null ? null : NpcMarket.Side.SELL));
                    }
                }
                shopNavigation(holder, page.number(), page.pages(),
                    target -> openTradingShop(target, access, page.number() - 1),
                    target -> openTradingShop(target, access, page.number() + 1));
                inventory.setItem(47, icon(Material.CLOCK, "<aqua>Refresh Prices</aqua>", MenuKit.withHint(List.of("<gray>Update prices, balance, and items in your bag.</gray>"), "refresh")));
                holder.actions.put(47, target -> openTradingShop(target, access, page.number()));
                inventory.setItem(MenuKit.backSlot(54), MenuKit.back(access.returnInstance() != null ? escape(access.merchant().name()) : "Shops"));
                holder.actions.put(MenuKit.backSlot(54), target -> backFromShop(target, access));
                inventory.setItem(MenuKit.closeSlot(54), MenuKit.close());
                holder.actions.put(MenuKit.closeSlot(54), Player::closeInventory);
                inventory.setItem(51, icon(Material.BOOK, "<white>Page " + (page.number()+1) + "/" + page.pages() + "</white>",
                    List.of("<gray>" + stock.size() + " products shown</gray>")));
                player.openInventory(inventory);
            } catch (RuntimeException failure) { shopUnavailable(player, failure); }
        }));
    }

    private void openTradingItem(Player player, ShopAccess access, String productId, int page, int requestedUnits, NpcMarket.Side review) {
        AccountId account = account(player);
        if (account == null || !currentShopAccess(player, account, access)) return;
        var product = access.shop().products().stream().filter(candidate -> candidate.id().equals(productId)).findFirst().orElse(null);
        if (product == null) return;
        int maximum = ShopItems.create(items, product, 1).getMaxStackSize();
        int units = Math.clamp(requestedUnits, 1, Math.min(64, maximum));
        ShopViewRequest request = requestShopView(player, account, access);
        reputation.value(account, access.merchant().factionId()).thenCombine(economy.balance(account),
            (points, balance) -> new long[]{points, balance}).whenComplete((values, error) -> sync(() -> {
            if (!currentShopRequest(player, request)) return;
            if (error != null) { shopUnavailable(player, error); return; }
            try {
                int points = (int)values[0];
                var standing = content.reputation(access.merchant().factionId(), points);
                if (points < product.minimumReputation() || !standing.tier().canTrade()) { openTradingShop(player, access, page); return; }
                var ask = product.basePrice() > 0 ? vendorTrading.quote(product, standing, NpcMarket.Side.BUY, units) : null;
                var bid = product.sellPrice() > 0 ? vendorTrading.quote(product, standing, NpcMarket.Side.SELL) : null;
                long buy = ask == null ? 0 : ask.marks(), sell = bid == null ? 0 : bid.marks();
                int matching = matchingStock(player, product);
                int remaining = product.sellPrice() > 0 ? shopLedger.remaining(account, access.shop().id(), productId, product.dailySellLimit()) : 0;
                boolean room = NpcVendorTrading.plan(player.getInventory().getStorageContents(), ShopItems.create(items, product, units), NpcMarket.Side.BUY) != null;
                var buyState = ShopControls.buyAvailability(values[1], buy, room);
                var sellState = ShopControls.sellAvailability(matching, product.amount(), remaining, sell);
                boolean trading = canTrade(player);
                NpcInventory holder = shopInventory(player, request, "<gold>" + escape(title(productId)) + "</gold>");
                Inventory inventory = holder.inventory;
                List<String> info = new ArrayList<>();
                wrap(product.description(), 42).forEach(line -> info.add("<gray>" + escape(line) + "</gray>"));
                info.add("<gray>Balance</gray> " + Chat.marksTag(values[1]));
                info.add("<gray>In your bag:</gray> <white>" + matching + " matching items</white>");
                info.add("<gray>" + escape(vendorTrading.trend(product)) + "</gray>");
                info.add("<gray>Prices are checked again when you confirm.</gray>");
                inventory.setItem(4, icon(Material.CHEST, "<gold>" + escape(access.shop().title()) + "</gold>", info));
                inventory.setItem(8, reputationIcon(access.merchant().factionId(), points));
                inventory.setItem(13, ShopItems.create(items, product, review == NpcMarket.Side.SELL ? product.amount() : units));
                var quantities = ShopControls.quantities(product.amount(), maximum);
                for (int index = 0; index < quantities.size(); index++) {
                    int amount = quantities.get(index), slot = ShopControls.quantitySlot(index, quantities.size());
                    inventory.setItem(slot, icon(amount == units ? Material.LIME_DYE : Material.PAPER,
                        "<aqua>Buy " + amount + "</aqua>", List.of(amount == units
                            ? "<green>Selected purchase quantity</green>" : MenuKit.hint("choose this quantity"))));
                    holder.actions.put(slot, target -> openTradingItem(target, access, productId, page, amount, NpcMarket.Side.BUY));
                }
                boolean confirmBuy = review == NpcMarket.Side.BUY;
                List<String> buyLore = new ArrayList<>(List.of(
                    "<gray>You receive:</gray> <white>" + units + " × " + escape(title(productId)) + "</white>",
                    "<gray>Total</gray> " + Chat.marksTag(buy)));
                if (!trading) buyLore.add("<red>Switch to Survival to trade.</red>");
                else if (!buyState.available()) {
                    buyLore.add("<red>" + escape(buyState.reason()) + "</red>");
                    if (buyState == ShopControls.Availability.INSUFFICIENT_MARKS)
                        buyLore.add("<gray>You need " + Chat.marksTag(buy - values[1]) + " more.</gray>");
                } else { buyLore.add(""); buyLore.add(confirmBuy ? MenuKit.hint("pay and receive your items") : MenuKit.hint("review the total")); }
                inventory.setItem(38, icon(!trading || !buyState.available() ? Material.GRAY_DYE : confirmBuy ? Material.LIME_DYE : Material.EMERALD,
                    !buyState.available() || !trading ? "<gray>Buy " + units + "</gray>" : confirmBuy ? "<green>Confirm Purchase</green>" : "<green>Buy " + units + "</green>", buyLore));
                if (trading && buyState.available()) holder.actions.put(38, target -> {
                    if (!currentShopView(target, account, access, holder)) return;
                    if (!confirmBuy) openTradingItem(target, access, productId, page, units, NpcMarket.Side.BUY);
                    else executeShopTrade(target, access, product, ask, holder, () -> openTradingItem(target, access, productId, page, units, null));
                });
                boolean confirmSell = review == NpcMarket.Side.SELL;
                List<String> sellLore = new ArrayList<>(List.of(
                    "<gray>You give:</gray> <white>" + product.amount() + " × " + escape(title(productId)) + "</white>",
                    "<gray>Payout</gray> " + Chat.marksTag(sell),
                    "<gray>In your bag:</gray> <white>" + matching + " matching items</white>"));
                if (product.sellPrice() > 0) sellLore.add("<gray>Daily sales left:</gray> <white>" + remaining + "/" + product.dailySellLimit() + " bundles</white>");
                if (!trading) sellLore.add("<red>Switch to Survival to trade.</red>");
                else if (!sellState.available()) sellLore.add("<red>" + escape(sellState.reason()) + "</red>");
                else { sellLore.add(""); sellLore.add(confirmSell ? MenuKit.hint("sell this bundle") : MenuKit.hint("review the payout")); }
                sellLore.add("<gray>Named or improved items stay in your bag.</gray>");
                if (product.sellPrice() > 0) sellLore.add("<dark_gray>Daily allowance resets at 00:00 UTC.</dark_gray>");
                inventory.setItem(42, icon(!trading || !sellState.available() ? Material.GRAY_DYE : confirmSell ? Material.LIME_DYE : Material.GOLD_INGOT,
                    !sellState.available() || !trading ? "<gray>Sell " + product.amount() + "</gray>" : confirmSell ? "<green>Confirm Sale</green>" : "<gold>Sell " + product.amount() + "</gold>", sellLore));
                if (trading && sellState.available()) holder.actions.put(42, target -> {
                    if (!currentShopView(target, account, access, holder)) return;
                    if (!confirmSell) openTradingItem(target, access, productId, page, units, NpcMarket.Side.SELL);
                    else executeShopTrade(target, access, product, bid, holder, () -> openTradingItem(target, access, productId, page, units, null));
                });
                inventory.setItem(47, icon(Material.CLOCK, "<aqua>Refresh Quote</aqua>", MenuKit.withHint(List.of("<gray>Update your balance, items, and prices.</gray>"), "refresh")));
                holder.actions.put(47, target -> openTradingItem(target, access, productId, page, units, null));
                inventory.setItem(MenuKit.backSlot(54), MenuKit.back(escape(access.shop().title())));
                holder.actions.put(MenuKit.backSlot(54), target -> openTradingShop(target, access, page));
                inventory.setItem(MenuKit.closeSlot(54), MenuKit.close());
                holder.actions.put(MenuKit.closeSlot(54), Player::closeInventory);
                player.openInventory(inventory);
            } catch (RuntimeException failure) { shopUnavailable(player, failure); }
        }));
    }

    private void executeShopTrade(Player player, ShopAccess access, NpcContent.ShopProduct product, NpcMarket.Quote quote,
                                  NpcInventory holder, Runnable reopen) {
        AccountId account = account(player);
        if (account == null || quote == null || !canTrade(player) || !currentShopView(player, account, access, holder)) return;
        if (!shopPending.add(player.getUniqueId())) { Chat.info(player, "Your trade is being processed. Please wait."); return; }
        reputation.value(account, access.merchant().factionId()).whenComplete((points, error) -> sync(() -> {
            shopPending.remove(player.getUniqueId());
            if (!currentShopView(player, account, access, holder) || !canTrade(player)) return;
            if (error != null || points == null) { shopUnavailable(player, error); return; }
            try {
                var standing = content.reputation(access.merchant().factionId(), points);
                if (!standing.tier().canTrade() || points < product.minimumReputation()) {
                    Chat.notice(player, "Your reputation changed. Review the shop again."); reopen.run(); return;
                }
                var refreshed = vendorTrading.quote(product, standing, quote.side(), quote.units());
                if (refreshed.marks() != quote.marks() || refreshed.revision() != quote.revision() || !quote.expires().isAfter(Instant.now())) {
                    Chat.notice(player, "The price changed. Review the new total before confirming."); reopen.run(); return;
                }
                vendorTrading.trade(player, account, access.shop(), product, quote,
                    () -> currentShopView(player, account, access, holder) && canTrade(player), reopen);
            } catch (RuntimeException failure) { shopUnavailable(player, failure); }
        }));
    }

    private void openShop(Player player, NpcInstance instance, String shopId, String returnNodeId, int requestedPage) {
        NpcContent.NpcDefinition npc = content.npcs().get(instance.definitionId());
        NpcContent.ShopDefinition shop = content.shops().get(shopId);
        AccountId account = account(player);
        if (npc == null || shop == null || account == null) return;
        if (vendorTrading != null && !marketContext(player, account, instance)) {
            Chat.error(player, "Trade in Survival with a merchant at the central spawn island. Use " + Chat.command("spawn") + "."); return;
        }
        NpcViewRequest request = requestView(player, account, instance);
        reputation.value(account, npc.factionId()).whenComplete((points, error) -> sync(() -> {
            if (error != null) { Chat.error(player, "The shop could not load. Please try again."); return; }
            if (!currentRequest(player, request) || content.shops().get(shopId) != shop
                || vendorTrading != null && !marketContext(player, account, instance)) return;
            NpcContent.ReputationView standing = content.reputation(npc.factionId(), points);
            NpcInventory holder = new NpcInventory(player.getUniqueId());
            holder.conversationToken = request.token(); holder.secondaryFallsBack = false;
            Inventory inventory = Bukkit.createInventory(holder, 54, message("<gold>" + escape(shop.title()) + "</gold>"));
            holder.inventory = inventory; decorate(inventory);
            List<String> header = new ArrayList<>();
            wrap(shop.description(), 42).forEach(line -> header.add("<gray>" + escape(line) + "</gray>"));
            header.add("<dark_gray>------------------------</dark_gray>");
            header.add("<gray>Click an item to inspect prices and quantities.</gray>");
            header.add("<gray>Nothing is spent until you confirm.</gray>");
            if (vendorTrading != null) header.add("<aqua>Prices follow server-wide supply and demand.</aqua>");
            inventory.setItem(4, icon(Material.CHEST, "<gold>" + escape(shop.title()) + "</gold>", header));
            inventory.setItem(8, reputationIcon(npc.factionId(), points));
            ShopLayout.Page<NpcContent.ShopProduct> page = ShopLayout.page(shop.products(), requestedPage);
            for (int index=0; index<page.entries().size(); index++) {
                var product=page.entries().get(index);
                boolean unlocked=points >= product.minimumReputation() && standing.tier().canTrade();
                var buyQuote=unlocked && vendorTrading!=null && product.basePrice()>0 ? vendorTrading.quote(product,standing,NpcMarket.Side.BUY) : null;
                var sellQuote=unlocked && vendorTrading!=null && product.sellPrice()>0 ? vendorTrading.quote(product,standing,NpcMarket.Side.SELL) : null;
                long ask=buyQuote!=null ? buyQuote.marks() : product.basePrice()>0 ? standing.price(product.basePrice()) : 0;
                long bid=sellQuote!=null ? sellQuote.marks() : product.sellPrice()>0 ? standing.payout(product.sellPrice()) : 0;
                List<String> lore=new ArrayList<>();
                wrap(product.description(),42).forEach(line->lore.add("<gray>"+escape(line)+"</gray>"));
                lore.add("<dark_gray>------------------------</dark_gray>");
                lore.add("<gray>Bundle:</gray> <white>"+product.amount()+" items</white>");
                if (unlocked && ask>0) lore.add("<gray>Buy</gray> " + Chat.marksTag(ask));
                if (unlocked && bid>0) lore.add("<gray>Sell</gray> " + Chat.marksTag(bid));
                if (unlocked && vendorTrading!=null) lore.add("<aqua>"+escape(vendorTrading.trend(product))+"</aqua>");
                if (product.sellPrice()>0) lore.add("<gray>Daily sales:</gray> <white>"
                    +shopLedger.remaining(account,shop.id(),product.id(),product.dailySellLimit())+"/"+product.dailySellLimit()+" bundles left</white>");
                if (unlocked) { lore.add(""); lore.add(MenuKit.hint("inspect")); } else lore.add("<red>Requires "+product.minimumReputation()+" reputation and trading access.</red>");
                int slot=ShopLayout.slot(index);
                inventory.setItem(slot,productIcon(product,unlocked,lore));
                if (unlocked) {
                    Consumer<Player> inspect=target->openShopItem(target,instance,shopId,product.id(),returnNodeId,page.number(),product.amount(),null);
                    holder.actions.put(slot,inspect); holder.secondaryActions.put(slot,inspect);
                }
            }
            shopNavigation(holder, page.number(), page.pages(),
                target->openShop(target,instance,shopId,returnNodeId,page.number()-1),
                target->openShop(target,instance,shopId,returnNodeId,page.number()+1));
            inventory.setItem(47,icon(Material.CLOCK,"<aqua>Refresh Prices</aqua>",MenuKit.withHint(List.of("<gray>Show the latest market quotes.</gray>"),"refresh")));
            holder.actions.put(47,target->openShop(target,instance,shopId,returnNodeId,page.number()));
            inventory.setItem(MenuKit.backSlot(54),MenuKit.back(escape(npc.name())));
            holder.actions.put(MenuKit.backSlot(54),target->openDialogue(target,instance,returnNodeId));
            inventory.setItem(MenuKit.closeSlot(54),MenuKit.close());
            holder.actions.put(MenuKit.closeSlot(54),Player::closeInventory);
            inventory.setItem(51,icon(Material.BOOK,"<white>Page "+(page.number()+1)+"/"+page.pages()+"</white>",
                List.of("<gray>"+shop.products().size()+" items stocked</gray>","<gray>Buy prices and payouts are separate.</gray>")));
            player.openInventory(inventory);
        }));
    }

    private void shopNavigation(NpcInventory holder,int page,int pages,Consumer<Player> previous,Consumer<Player> next) {
        if (page>0) { holder.inventory.setItem(MenuKit.previousSlot(54),MenuKit.previous(page,pages)); holder.actions.put(MenuKit.previousSlot(54),previous); }
        if (page+1<pages) { holder.inventory.setItem(MenuKit.nextSlot(54),MenuKit.next(page,pages)); holder.actions.put(MenuKit.nextSlot(54),next); }
    }

    private void openShopItem(Player player,NpcInstance instance,String shopId,String productId,String returnNodeId,
                              int page,int requestedUnits,NpcMarket.Side review) {
        var npc=content.npcs().get(instance.definitionId()); var shop=content.shops().get(shopId);
        AccountId account=account(player);
        if (npc==null || shop==null || account==null) return;
        var product=shop.products().stream().filter(p->p.id().equals(productId)).findFirst().orElse(null);
        if (product==null || vendorTrading!=null && !marketContext(player,account,instance)) return;
        int maximum=ShopItems.create(items,product,1).getMaxStackSize();
        int units=vendorTrading==null ? product.amount() : Math.clamp(requestedUnits,1,Math.min(64,maximum));
        NpcViewRequest request=requestView(player,account,instance);
        reputation.value(account,npc.factionId()).thenCombine(economy.balance(account),(points,balance)->new long[]{points,balance})
            .whenComplete((values,error)->sync(()->{
                if (error!=null) { Chat.error(player, "The shop could not load. Please try again."); return; }
                if (!currentRequest(player,request) || content.shops().get(shopId)!=shop
                    || vendorTrading!=null && !marketContext(player,account,instance)) return;
                int points=(int)values[0];
                var standing=content.reputation(npc.factionId(),points);
                boolean unlocked=points>=product.minimumReputation() && standing.tier().canTrade();
                if (!unlocked) { openShop(player,instance,shopId,returnNodeId,page); return; }
                var ask=vendorTrading!=null && product.basePrice()>0 ? vendorTrading.quote(product,standing,NpcMarket.Side.BUY,units) : null;
                var bid=vendorTrading!=null && product.sellPrice()>0 ? vendorTrading.quote(product,standing,NpcMarket.Side.SELL) : null;
                long buy=ask!=null ? ask.marks() : product.basePrice()>0 ? standing.price(product.basePrice()) : 0;
                long sell=bid!=null ? bid.marks() : product.sellPrice()>0 ? standing.payout(product.sellPrice()) : 0;
                int remaining=product.sellPrice()>0 ? shopLedger.remaining(account,shopId,productId,product.dailySellLimit()) : 0;
                NpcInventory holder=new NpcInventory(player.getUniqueId());
                holder.conversationToken=request.token(); holder.secondaryFallsBack=false;
                holder.inventory=Bukkit.createInventory(holder,54,message("<gold>"+escape(title(productId))+"</gold>"));
                Inventory inventory=holder.inventory; decorate(inventory);
                List<String> info=new ArrayList<>();
                wrap(product.description(),42).forEach(line->info.add("<gray>"+escape(line)+"</gray>"));
                info.add("<gray>Balance</gray> " + Chat.marksTag(values[1]));
                if (vendorTrading!=null) info.add("<aqua>"+escape(vendorTrading.trend(product))+"</aqua>");
                info.add("<gray>Quotes last 30 seconds.</gray>");
                info.add("<gray>Changed prices need a new review.</gray>");
                if (review==NpcMarket.Side.SELL) info.add("<gold>Reviewing a sale of "+product.amount()+" items.</gold>");
                inventory.setItem(4,icon(Material.CHEST,"<gold>"+escape(shop.title())+"</gold>",info));
                inventory.setItem(8,reputationIcon(npc.factionId(),points));
                inventory.setItem(13,ShopItems.create(items,product,review==NpcMarket.Side.SELL ? product.amount() : units));
                int[] quantities={1,16,64}, quantitySlots={20,22,24};
                for (int i=0;i<quantities.length;i++) {
                    int amount=quantities[i],slot=quantitySlots[i];
                    boolean available=amount<=maximum && (vendorTrading!=null || amount==product.amount());
                    inventory.setItem(slot,icon(!available ? Material.GRAY_DYE : amount==units ? Material.LIME_DYE : Material.PAPER,
                        (available?"<aqua>":"<gray>")+"Buy "+amount+(available?"</aqua>":"</gray>"),
                        List.of(!available?"<gray>This item uses a smaller bundle.</gray>":amount==units?"<green>Selected</green>":MenuKit.hint("change quantity"))));
                    if (available) holder.actions.put(slot,target->openShopItem(target,instance,shopId,productId,returnNodeId,page,amount,null));
                }
                if (buy>0) {
                    boolean confirming=review==NpcMarket.Side.BUY;
                    inventory.setItem(38,icon(confirming?Material.LIME_DYE:Material.EMERALD,
                        confirming?"<green>Confirm Purchase</green>":"<gold>Buy "+units+"</gold>",
                        List.of("<gray>You receive:</gray> <white>"+units+" × "+escape(title(productId))+"</white>",
                            "<gray>Total</gray> " + Chat.marksTag(buy),
                            "<gray>Requires room for every item.</gray>","",confirming?MenuKit.hint("pay and collect"):MenuKit.hint("review this purchase"))));
                    holder.actions.put(38,target->{
                        if (!currentConversation(target,account,instance,holder)) return;
                        if (!confirming) openShopItem(target,instance,shopId,productId,returnNodeId,page,units,NpcMarket.Side.BUY);
                        else if (vendorTrading!=null) marketTrade(target,instance,shop,product,ask,holder,returnNodeId,
                            ()->openShopItem(target,instance,shopId,productId,returnNodeId,page,units,null));
                        else buy(target,instance,shop,product,returnNodeId);
                    });
                }
                if (sell>0) {
                    boolean confirming=review==NpcMarket.Side.SELL;
                    inventory.setItem(42,icon(remaining==0?Material.GRAY_DYE:confirming?Material.LIME_DYE:Material.GOLD_INGOT,
                        remaining==0?"<gray>Daily Limit Reached</gray>":confirming?"<green>Confirm Sale</green>":"<green>Sell "+product.amount()+"</green>",
                        List.of("<gray>One bundle:</gray> <white>"+product.amount()+" × "+escape(title(productId))+"</white>",
                            "<gray>Payout</gray> " + Chat.marksTag(sell),
                            "<gray>Daily allowance:</gray> <white>"+remaining+"/"+product.dailySellLimit()+" bundles left</white>",
                            "<gray>Only matching items in your main inventory.</gray>",
                            "<gray>Named, enchanted, and improved gear is not buyback stock.</gray>",
                            "<dark_gray>Allowance resets at 00:00 UTC.</dark_gray>",
                            "",confirming?MenuKit.hint("turn in this bundle"):MenuKit.hint("review this sale"))));
                    if (remaining>0) holder.actions.put(42,target->{
                        if (!currentConversation(target,account,instance,holder)) return;
                        if (!confirming) openShopItem(target,instance,shopId,productId,returnNodeId,page,units,NpcMarket.Side.SELL);
                        else if (vendorTrading!=null) marketTrade(target,instance,shop,product,bid,holder,returnNodeId,
                            ()->openShopItem(target,instance,shopId,productId,returnNodeId,page,units,null));
                        else sell(target,instance,shop,product,returnNodeId);
                    });
                }
                inventory.setItem(MenuKit.backSlot(54),MenuKit.back(escape(shop.title())));
                holder.actions.put(MenuKit.backSlot(54),target->openShop(target,instance,shopId,returnNodeId,page));
                inventory.setItem(47,icon(Material.CLOCK,"<aqua>Refresh Quote</aqua>",MenuKit.withHint(List.of("<gray>Clear the review and show current prices.</gray>"),"refresh")));
                holder.actions.put(47,target->openShopItem(target,instance,shopId,productId,returnNodeId,page,units,null));
                inventory.setItem(MenuKit.closeSlot(54),MenuKit.close());
                holder.actions.put(MenuKit.closeSlot(54),Player::closeInventory);
                player.openInventory(inventory);
            }));
    }

    private boolean marketContext(Player player, AccountId account, NpcInstance instance) {
        LivingEntity body = liveByInstance.get(instance.id());
        double radius = Math.clamp(plugin.getConfig().getDouble("skyblock.market.hub-radius", 256), 16, 512);
        return !player.isDead() && player.getGameMode() == org.bukkit.GameMode.SURVIVAL
            && currentNpcContext(player, account, instance) && plugin.travel().nearCentralSpawn(instance.location(), radius)
            && body != null && body.isValid() && player.hasLineOfSight(body);
    }
    private void marketTrade(Player player, NpcInstance instance, NpcContent.ShopDefinition shop,
                             NpcContent.ShopProduct product, NpcMarket.Quote quote, NpcInventory holder, String returnNodeId) {
        marketTrade(player, instance, shop, product, quote, holder, returnNodeId,
            () -> openShop(player, instance, shop.id(), returnNodeId));
    }

    private void marketTrade(Player player, NpcInstance instance, NpcContent.ShopDefinition shop,
                             NpcContent.ShopProduct product, NpcMarket.Quote quote, NpcInventory holder,
                             String returnNodeId, Runnable reopen) {
        AccountId account = account(player); NpcContent.NpcDefinition npc = content.npcs().get(instance.definitionId());
        if (account == null || npc == null || quote == null || !currentConversation(player, account, instance, holder)
            || !marketContext(player, account, instance) || !shopPending.add(player.getUniqueId())) return;
        reputation.value(account, npc.factionId()).whenComplete((points, error) -> sync(() -> {
            shopPending.remove(player.getUniqueId());
            if (error != null || points == null || !currentConversation(player, account, instance, holder) || !marketContext(player, account, instance)) return;
            try {
                NpcContent.ReputationView standing = content.reputation(npc.factionId(), points);
                if (!standing.tier().canTrade() || points < product.minimumReputation()
                    || content.shops().get(shop.id()) != shop) { Chat.notice(player, "This shop changed. Speak to the merchant again."); return; }
                NpcMarket.Quote refreshed = vendorTrading.quote(product, standing, quote.side(), quote.units());
                if (refreshed.marks() != quote.marks() || refreshed.revision() != quote.revision() || !quote.expires().isAfter(Instant.now())) {
                    Chat.notice(player, "That quote changed. Review the refreshed price before trading."); reopen.run(); return;
                }
                vendorTrading.trade(player, account, shop, product, quote,
                    () -> currentConversation(player, account, instance, holder) && marketContext(player, account, instance),
                    reopen);
            } catch (RuntimeException failure) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Vendor quote failed", failure); Chat.error(player, "The market is unavailable. Please contact staff."); }
        }));
    }

    private void buy(
        Player player,
        NpcInstance instance,
        NpcContent.ShopDefinition shop,
        NpcContent.ShopProduct product,
        String returnNodeId
    ) {
        if (product.basePrice() <= 0L || !shopPending.add(player.getUniqueId())) {
            player.sendRichMessage("<gray>A vendor transaction is already being processed.</gray>");
            return;
        }
        NpcContent.NpcDefinition npc = content.npcs().get(instance.definitionId());
        AccountId account = account(player);
        if (npc == null || account == null) {
            shopPending.remove(player.getUniqueId());
            return;
        }
        reputation.value(account, npc.factionId()).thenCompose(points -> {
            NpcContent.ReputationView standing = content.reputation(npc.factionId(), points);
            if (points < product.minimumReputation() || !standing.tier().canTrade()) {
                return CompletableFuture.completedFuture(new PurchaseResult(PurchaseStatus.LOCKED, 0L));
            }
            long price = standing.price(product.basePrice());
            String operation = "npc-shop:" + UUID.randomUUID();
            return economy.debit(account, price,
                new EconomyService.TransactionReason("npc.shop", shop.id() + "/" + product.id()),
                operation).thenApply(result -> new PurchaseResult(
                    result.status() == EconomyService.TransactionResult.Status.APPLIED
                        ? PurchaseStatus.PAID : PurchaseStatus.INSUFFICIENT,
                    price
                ));
        }).whenComplete((result, error) -> sync(() -> {
            shopPending.remove(player.getUniqueId());
            if (error != null || result == null) {
                player.sendRichMessage("<red>The vendor could not complete that purchase.</red>");
                return;
            }
            if (result.status() == PurchaseStatus.LOCKED) {
                player.sendRichMessage("<red>Your standing does not allow that purchase.</red>");
                openShop(player, instance, shop.id(), returnNodeId);
                return;
            }
            if (result.status() == PurchaseStatus.INSUFFICIENT) {
                player.sendRichMessage("<red>You do not have enough Marks.</red>");
                openShop(player, instance, shop.id(), returnNodeId);
                return;
            }
            if (!player.isOnline()) {
                economy.credit(account, result.price(),
                    new EconomyService.TransactionReason("npc.shop.refund", "Player left before delivery"),
                    "npc-shop-refund:" + UUID.randomUUID());
                return;
            }
            ItemStack delivered = ShopItems.create(items, product, product.amount());
            player.getInventory().addItem(delivered).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            player.sendRichMessage("<green>Purchased " + product.amount() + " × " + escape(title(product.id()))
                + " for " + Chat.marksTag(result.price()) + ".</green>");
            openShop(player, instance, shop.id(), returnNodeId);
        }));
    }

    private void sell(
        Player player,
        NpcInstance instance,
        NpcContent.ShopDefinition shop,
        NpcContent.ShopProduct product,
        String returnNodeId
    ) {
        if (product.sellPrice() <= 0L || !shopPending.add(player.getUniqueId())) {
            player.sendRichMessage("<gray>A vendor transaction is already being processed.</gray>");
            return;
        }
        NpcContent.NpcDefinition npc = content.npcs().get(instance.definitionId());
        AccountId account = account(player);
        if (npc == null || account == null) {
            shopPending.remove(player.getUniqueId());
            return;
        }
        reputation.value(account, npc.factionId()).whenComplete((points, error) -> sync(() -> {
            if (error != null || points == null || !player.isOnline()) {
                shopPending.remove(player.getUniqueId());
                return;
            }
            NpcContent.ReputationView standing = content.reputation(npc.factionId(), points);
            if (points < product.minimumReputation() || !standing.tier().canTrade()) {
                shopPending.remove(player.getUniqueId());
                player.sendRichMessage("<red>Your standing does not allow that sale.</red>");
                openShop(player, instance, shop.id(), returnNodeId);
                return;
            }
            boolean reserved;
            try {
                reserved = shopLedger.reserve(account, shop.id(), product.id(), product.dailySellLimit());
            } catch (RuntimeException exception) {
                shopPending.remove(player.getUniqueId());
                player.sendRichMessage("<red>The vendor ledger is unavailable. Nothing was taken.</red>");
                plugin.getLogger().severe("Could not reserve NPC sale quota: " + exception.getMessage());
                return;
            }
            if (!reserved) {
                shopPending.remove(player.getUniqueId());
                player.sendRichMessage("<yellow>You have reached today's vendor limit for that item.</yellow>");
                openShop(player, instance, shop.id(), returnNodeId);
                return;
            }
            if (!removeProduct(player, product)) {
                shopLedger.rollback(account, shop.id(), product.id());
                shopPending.remove(player.getUniqueId());
                player.sendRichMessage("<red>You need " + product.amount() + " matching "
                    + escape(title(product.id())) + " to sell that bundle.</red>");
                openShop(player, instance, shop.id(), returnNodeId);
                return;
            }
            long payout = standing.payout(product.sellPrice());
            economy.credit(account, payout,
                new EconomyService.TransactionReason("npc.shop.sale", shop.id() + "/" + product.id()),
                "npc-shop-sale:" + UUID.randomUUID()).whenComplete((payment, paymentError) -> sync(() -> {
                    shopPending.remove(player.getUniqueId());
                    if (paymentError != null || payment == null
                        || (payment.status() != EconomyService.TransactionResult.Status.APPLIED
                        && payment.status() != EconomyService.TransactionResult.Status.DUPLICATE)) {
                        shopLedger.rollback(account, shop.id(), product.id());
                        refundProduct(player, product);
                        player.sendRichMessage("<red>The sale failed; your items were returned.</red>");
                        return;
                    }
                    player.sendRichMessage("<green>Sold " + product.amount() + " × "
                        + escape(title(product.id())) + " for " + Chat.marksTag(payout) + ".</green>");
                    openShop(player, instance, shop.id(), returnNodeId);
                }));
        }));
    }

    private boolean removeProduct(Player player, NpcContent.ShopProduct product) {
        ItemStack template = productTemplate(product);
        ItemStack[] contents = player.getInventory().getContents();
        int available = 0;
        for (ItemStack item : contents) {
            if (item != null && item.isSimilar(template)) {
                available += item.getAmount();
            }
        }
        if (available < product.amount()) {
            return false;
        }
        int remaining = product.amount();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || !item.isSimilar(template)) {
                continue;
            }
            int removed = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - removed);
            player.getInventory().setItem(slot, item.getAmount() <= 0 ? null : item);
            remaining -= removed;
        }
        return remaining == 0;
    }

    private void refundProduct(Player player, NpcContent.ShopProduct product) {
        if (!player.isOnline()) {
            plugin.getLogger().severe("Could not return failed NPC sale to offline player " + player.getName());
            return;
        }
        ItemStack returned = productTemplate(product);
        returned.setAmount(product.amount());
        player.getInventory().addItem(returned).values()
            .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private ItemStack productTemplate(NpcContent.ShopProduct product) {
        return ShopItems.create(items, product, 1);
    }

    public boolean showShops(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || account(player) == null) return true;
        String query = String.join(" ", args).strip();
        if (query.length() > 100) {
            Chat.error(player, "Keep your shop search under 100 characters.");
            return true;
        }
        String shopId = query.toLowerCase(Locale.ROOT);
        if (vendorTrading != null && content.shops().containsKey(shopId)) {
            openRemoteShop(player, shopId, "", 0);
            return true;
        }
        openShopDirectory(player, query, 0);
        return true;
    }

    public List<String> completeShops(String[] args) {
        if (args.length != 1) return List.of();
        Set<String> choices = new java.util.TreeSet<>();
        choices.addAll(content.shops().keySet());
        content.npcs().values().forEach(npc -> {
            if (npc.shopId() != null) {
                choices.add(npc.id());
                for (String word : npc.name().toLowerCase(Locale.ROOT).split("\\s+")) choices.add(word);
            }
        });
        content.shops().values().forEach(shop -> shop.products().forEach(product -> {
            choices.add(product.id());
            if (product.material() != null) {
                choices.add(product.material().name().toLowerCase(Locale.ROOT));
                for (String word : product.material().name().toLowerCase(Locale.ROOT).split("_")) choices.add(word);
            }
        }));
        return List.copyOf(choices);
    }

    private boolean currentBrowseView(Player player, NpcInventory holder) {
        if (!plugin.isEnabled() || !player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player
            || !sessions.account(player.getUniqueId()).filter(holder.browseAccount::equals).isPresent()) return false;
        if (holder.browseContent != content) {
            Chat.notice(player, "The shop directory changed. Open " + Chat.command("shops") + " again.");
            return false;
        }
        return true;
    }

    private NpcInventory browseInventory(Player player, String title) {
        NpcInventory holder = new NpcInventory(player.getUniqueId());
        holder.browseAccount = sessions.account(player.getUniqueId()).orElseThrow();
        holder.browseContent = content;
        holder.secondaryFallsBack = false;
        holder.inventory = Bukkit.createInventory(holder, 54, message(title));
        decorate(holder.inventory);
        return holder;
    }

    private List<NpcCatalog.Entry> shopkeepers(String shopId) {
        return new NpcCatalog(content).entries().stream()
            .filter(entry -> entry.shops().stream().anyMatch(shop -> shop.id().equals(shopId))).toList();
    }

    private List<String> merchantDirections(String shopId) {
        Set<String> definitions = shopkeepers(shopId).stream().map(entry -> entry.definition().id())
            .collect(java.util.stream.Collectors.toSet());
        List<String> directions = new ArrayList<>();
        for (NpcInstance instance : instances.values()) {
            if (!definitions.contains(instance.definitionId())) continue;
            Location location = instance.location();
            if (location == null) continue;
            directions.add(location.getWorld().getName() + " · " + location.getBlockX() + ", "
                + location.getBlockY() + ", " + location.getBlockZ());
            if (directions.size() == 3) break;
        }
        return directions;
    }

    private void openShopDirectory(Player player, String query, int requested) {
        if (account(player) == null) return;
        NpcCatalog catalog = new NpcCatalog(content);
        String normalized = query.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').strip();
        List<String> terms = normalized.isBlank() ? List.of() : List.of(normalized.split("\\s+"));
        Set<String> matching = catalog.entries().stream()
            .filter(entry -> entry.categories().contains(NpcCatalog.Category.SHOPKEEPERS))
            .filter(entry -> terms.stream().allMatch(entry.searchText()::contains))
            .flatMap(entry -> entry.shops().stream()).map(NpcContent.ShopDefinition::id)
            .collect(java.util.stream.Collectors.toSet());
        var page = ShopLayout.page(content.shops().values().stream().filter(shop -> matching.contains(shop.id()))
            .sorted(java.util.Comparator.comparing(NpcContent.ShopDefinition::title, String.CASE_INSENSITIVE_ORDER)).toList(), requested);
        NpcInventory holder = browseInventory(player, "<gold>Shops</gold>");
        Inventory inventory = holder.inventory;
        inventory.setItem(4, icon(Material.CHEST, "<gold>Shops</gold>", List.of(
            vendorTrading == null ? "<gray>Browse supplies and find their merchants.</gray>" : "<gray>Buy supplies and sell surplus from anywhere.</gray>",
            vendorTrading == null ? "<gray>Visit the counter to buy or sell.</gray>" : "<gray>Choose a shop, then an item.</gray>",
            "<gray>Search:</gray> <white>" + escape(query.isBlank() ? "All shops" : query) + "</white>")));
        inventory.setItem(8, icon(Material.SPYGLASS, "<aqua>Search Stock</aqua>", MenuKit.withHint(List.of(
            "<gray>Use /shops [item, shop, or merchant].</gray>", "<gray>Example: /shops sapling</gray>"), "search")));
        holder.actions.put(8, target -> {
            target.closeInventory();
            Chat.send(target, Chat.usage("/shops [item, shop, or merchant]"));
        });
        for (int index = 0; index < page.entries().size(); index++) {
            var shop = page.entries().get(index);
            List<String> lore = new ArrayList<>();
            wrap(shop.description(), 42).forEach(line -> lore.add("<gray>" + escape(line) + "</gray>"));
            lore.add("");
            shopkeepers(shop.id()).forEach(entry -> lore.add("<aqua>" + escape(entry.definition().name()) + "</aqua>"));
            List<String> directions = merchantDirections(shop.id());
            if (!directions.isEmpty()) directions.forEach(line -> lore.add("<dark_gray>Optional storefront: " + escape(line) + "</dark_gray>"));
            lore.add("<gray>Stock:</gray> <white>" + shop.products().size() + " products</white>");
            lore.add("");
            lore.add(vendorTrading == null ? MenuKit.hint("browse the shelves") : MenuKit.hint("buy or sell"));
            inventory.setItem(ShopLayout.slot(index), icon(shopCounterIcon(shop.id()), "<gold>" + escape(shop.title()) + "</gold>", lore));
            holder.actions.put(ShopLayout.slot(index), target -> openRemoteShop(target, shop.id(), query, page.number()));
        }
        if (page.entries().isEmpty()) inventory.setItem(22, icon(Material.BARRIER, "<gray>No matching shops</gray>",
            List.of("<gray>Try /shops with a different item or name.</gray>")));
        shopNavigation(holder, page.number(), page.pages(),
            target -> openShopDirectory(target, query, page.number() - 1), target -> openShopDirectory(target, query, page.number() + 1));
        inventory.setItem(MenuKit.backSlot(54), MenuKit.back("World & Trade"));
        holder.actions.put(MenuKit.backSlot(54), target -> plugin.menus().open(target, MenuManager.View.WORLD));
        inventory.setItem(MenuKit.closeSlot(54), MenuKit.close());
        holder.actions.put(MenuKit.closeSlot(54), Player::closeInventory);
        player.openInventory(inventory);
    }

    private void openStockGuide(Player player, String shopId, String query, int directoryPage, int requested) {
        if (account(player) == null) return;
        var shop = content.shops().get(shopId);
        if (shop == null) { openShopDirectory(player, query, directoryPage); return; }
        var page = ShopLayout.page(shop.products(), requested);
        NpcInventory holder = browseInventory(player, "<gold>" + escape(shop.title()) + "</gold>");
        Inventory inventory = holder.inventory;
        inventory.setItem(4, icon(Material.BOOK, "<gold>Stock Guide</gold>", List.of(
            "<gray>Reference prices use neutral reputation.</gray>",
            "<gray>Your price is confirmed at the counter.</gray>",
            "<gray>This guide cannot buy or sell items.</gray>")));
        List<String> directions = merchantDirections(shopId);
        inventory.setItem(8, icon(Material.COMPASS, "<aqua>Find the Merchant</aqua>", directions.isEmpty()
            ? List.of("<gray>This counter is not open at the hub yet.</gray>")
            : MenuKit.withHint(directions.stream().map(line -> "<gray>" + escape(line) + "</gray>").toList(), "get directions")));
        holder.actions.put(8, target -> {
            target.closeInventory();
            if (directions.isEmpty()) Chat.notice(target, "This counter is not open at the hub yet.");
            else {
                target.sendRichMessage("<gold>" + escape(shop.title()) + "</gold> <gray>· Visit the merchant to trade.</gray>");
                directions.forEach(line -> Chat.info(target, escape(line)));
                Chat.info(target, "Travel to the hub with " + Chat.command("spawn") + ".");
            }
        });
        for (int index = 0; index < page.entries().size(); index++) {
            var product = page.entries().get(index);
            var snapshot = vendorTrading == null ? null : vendorTrading.snapshot(product, product.amount());
            List<String> lore = new ArrayList<>();
            wrap(product.description(), 42).forEach(line -> lore.add("<gray>" + escape(line) + "</gray>"));
            lore.add("");
            lore.add("<gray>Bundle:</gray> <white>" + product.amount() + " items</white>");
            long buy = snapshot == null ? product.basePrice() : snapshot.buyMarks();
            long sell = snapshot == null ? product.sellPrice() : snapshot.sellMarks();
            if (buy > 0) lore.add("<gray>Buy</gray> " + Chat.marksTag(buy));
            if (sell > 0) lore.add("<gray>Sell</gray> " + Chat.marksTag(sell) + " <gray>· " + product.dailySellLimit() + " bundles/day</gray>");
            if (product.minimumReputation() > 0) lore.add("<gray>Requires " + product.minimumReputation() + " reputation.</gray>");
            if (snapshot != null) lore.add("<gray>" + escape(snapshot.trend()) + "</gray>");
            lore.add("<gray>Visit the merchant to trade.</gray>");
            inventory.setItem(ShopLayout.slot(index), productIcon(product, true, lore));
        }
        shopNavigation(holder, page.number(), page.pages(),
            target -> openStockGuide(target, shopId, query, directoryPage, page.number() - 1),
            target -> openStockGuide(target, shopId, query, directoryPage, page.number() + 1));
        inventory.setItem(47, icon(Material.CLOCK, "<aqua>Refresh Prices</aqua>", List.of(MenuKit.hint("refresh"))));
        holder.actions.put(47, target -> openStockGuide(target, shopId, query, directoryPage, page.number()));
        inventory.setItem(MenuKit.backSlot(54), MenuKit.back("Shops"));
        holder.actions.put(MenuKit.backSlot(54), target -> openShopDirectory(target, query, directoryPage));
        inventory.setItem(MenuKit.closeSlot(54), MenuKit.close());
        holder.actions.put(MenuKit.closeSlot(54), Player::closeInventory);
        player.openInventory(inventory);
    }

    private Material shopCounterIcon(String id) {
        return switch (id) {
            case "farmstead_supplies" -> Material.WHEAT;
            case "timber_yard" -> Material.OAK_LOG;
            case "quarry_supplies" -> Material.IRON_PICKAXE;
            case "masons_stock" -> Material.BRICKS;
            case "fishery_supplies" -> Material.COD;
            case "apothecary_supplies" -> Material.BREWING_STAND;
            case "workshop_parts" -> Material.REDSTONE;
            case "scholar_supplies" -> Material.BOOK;
            case "warden_supplies" -> Material.LEATHER_CHESTPLATE;
            case "delver_supplies" -> Material.COMPASS;
            default -> Material.CHEST;
        };
    }

    private boolean catalogCommand(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !staffAccess(player)) return true;
        NpcCatalog.Category category = args.length > 1 ? NpcCatalog.Category.parse(args[1]) : NpcCatalog.Category.ALL;
        if (category == null) {
            Chat.usage(player, "/npc catalog [all|shopkeepers|services|quests] [search]");
            return true;
        }
        String query = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "";
        if (query.length() > 100) {
            Chat.error(player, "Keep the NPC search under 100 characters.");
            return true;
        }
        openCatalog(player, category, query, 0);
        return true;
    }

    private boolean staffAccess(Player player) {
        if (!player.isOnline() || !player.hasPermission("nerdsmp.npc.admin")) {
            Chat.noPermission(player);
            return false;
        }
        return account(player) != null;
    }

    private boolean currentStaffView(Player player, NpcInventory holder) {
        if (!player.isOnline() || !player.hasPermission("nerdsmp.npc.admin")
            || !sessions.account(player.getUniqueId()).filter(holder.staffAccount::equals).isPresent()) return false;
        if (holder.staffContent != content) {
            Chat.notice(player, "NPC definitions changed. Open " + Chat.command("npc catalog") + " again.");
            return false;
        }
        return true;
    }

    private NpcInventory staffInventory(Player player, int size, String title) {
        NpcInventory holder = new NpcInventory(player.getUniqueId());
        holder.staffAccount = sessions.account(player.getUniqueId()).orElseThrow();
        holder.staffContent = content;
        holder.secondaryFallsBack = false;
        holder.inventory = Bukkit.createInventory(holder, size, message(title));
        decorate(holder.inventory);
        return holder;
    }

    private void openCatalog(Player player, NpcCatalog.Category category, String query, int requested) {
        if (!staffAccess(player)) return;
        NpcCatalog catalog = new NpcCatalog(content);
        NpcCatalog.Page page = catalog.page(category, query, requested, 21);
        NpcInventory holder = staffInventory(player, 54, "<dark_purple>NPC catalog</dark_purple>");
        Inventory inventory = holder.inventory;
        int[] categorySlots = {0, 2, 4, 6};
        for (NpcCatalog.Category filter : NpcCatalog.Category.values()) {
            int slot = categorySlots[filter.ordinal()];
            ItemStack button = icon(filter.icon(), (category == filter ? "<green>" : "<yellow>")
                + escape(filter.title()) + (category == filter ? "</green>" : "</yellow>"),
                List.of(category == filter ? "<gray>Selected</gray>" : "<gray>Click to browse.</gray>"));
            inventory.setItem(slot, button);
            holder.actions.put(slot, target -> openCatalog(target, filter, query, 0));
        }
        inventory.setItem(8, icon(Material.SPYGLASS, "<aqua>Search definitions</aqua>", List.of(
            "<gray>Search:</gray> <white>" + escape(query.isBlank() ? "Any" : query) + "</white>",
            "<gray>Names, roles, services, and shop items.</gray>",
            "<yellow>Click for a search command.</yellow>")));
        holder.actions.put(8, target -> {
            target.closeInventory();
            target.sendRichMessage("<aqua><click:suggest_command:'/npc catalog " + category.key()
                + " '>[Click to search NPCs]</click></aqua>");
        });
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int index = 0; index < page.entries().size(); index++) {
            NpcCatalog.Entry entry = page.entries().get(index);
            List<String> lore = new ArrayList<>(List.of(
                "<gray>" + escape(entry.definition().title()) + "</gray>",
                "<dark_gray>------------------------</dark_gray>",
                "<gray>Faction:</gray> <white>" + escape(entry.faction()) + "</white>"));
            entry.shops().forEach(shop -> lore.add("<green>Shop:</green> <white>" + escape(shop.title()) + "</white>"));
            if (!entry.services().isEmpty()) lore.add("<aqua>Offers:</aqua> <white>"
                + escape(String.join(", ", entry.services())) + "</white>");
            lore.add("<gray>Placed:</gray> <white>" + placedCount(entry.definition().id()) + "</white>");
            lore.add("<dark_gray>" + escape(entry.definition().id()) + "</dark_gray>");
            lore.add("<yellow>Click to inspect and place.</yellow>");
            int slot = slots[index];
            Material material = !entry.shops().isEmpty() ? Material.EMERALD
                : entry.categories().contains(NpcCatalog.Category.QUESTS) ? Material.WRITABLE_BOOK : Material.VILLAGER_SPAWN_EGG;
            inventory.setItem(slot, icon(material, "<gold>" + escape(entry.definition().name()) + "</gold>", lore));
            holder.actions.put(slot, target -> openCatalogEntry(target, entry.definition().id(), category, query, page.number()));
        }
        if (page.entries().isEmpty()) inventory.setItem(31, icon(Material.BARRIER, "<gray>No matches</gray>",
            List.of("<gray>Choose another category or search.</gray>")));
        if (page.number() > 0) {
            inventory.setItem(MenuKit.previousSlot(54), MenuKit.previous(page.number(), page.pages()));
            holder.actions.put(MenuKit.previousSlot(54), target -> openCatalog(target, category, query, page.number() - 1));
        }
        inventory.setItem(49, icon(Material.BOOK, "<aqua>" + escape(category.title()) + "</aqua>", List.of(
            "<gray>Page " + (page.number() + 1) + " of " + page.pages() + " • " + page.matches() + " definitions</gray>",
            "<gray>Anchors: " + instances.size() + "/" + maximumInstances + "</gray>",
            "<gray>Select an NPC, then choose Place here.</gray>")));
        if (page.number() + 1 < page.pages()) {
            inventory.setItem(MenuKit.nextSlot(54), MenuKit.next(page.number(), page.pages()));
            holder.actions.put(MenuKit.nextSlot(54), target -> openCatalog(target, category, query, page.number() + 1));
        }
        player.openInventory(inventory);
    }

    private long placedCount(String definitionId) {
        return instances.values().stream().filter(instance -> definitionId.equals(instance.definitionId())).count();
    }

    private void openCatalogEntry(Player player, String definitionId, NpcCatalog.Category category, String query, int page) {
        if (!staffAccess(player)) return;
        NpcCatalog.Entry entry = new NpcCatalog(content).find(definitionId);
        if (entry == null) { openCatalog(player, category, query, page); return; }
        NpcInventory holder = staffInventory(player, 54, "<dark_purple>Place NPC</dark_purple>");
        Inventory inventory = holder.inventory;
        Location anchor = player.getLocation().clone();
        inventory.setItem(4, icon(Material.VILLAGER_SPAWN_EGG, "<gold>" + escape(entry.definition().name()) + "</gold>",
            List.of("<gray>" + escape(entry.definition().title()) + "</gray>",
                "<gray>Faction:</gray> <white>" + escape(entry.faction()) + "</white>",
                "<gray>Placed:</gray> <white>" + placedCount(definitionId) + "</white>",
                "<dark_gray>" + escape(definitionId) + "</dark_gray>")));
        List<String> offers = new ArrayList<>();
        for (String service : entry.services()) offers.add("<aqua>• " + escape(service) + "</aqua>");
        for (NpcContent.ShopDefinition shop : entry.shops()) {
            offers.add("<green>" + escape(shop.title()) + "</green>");
            wrap(shop.description(), 42).forEach(line -> offers.add("<gray>" + escape(line) + "</gray>"));
            for (NpcContent.ShopProduct product : shop.products()) {
                String directions = product.basePrice() > 0 && product.sellPrice() > 0 ? "Buy / Sell"
                    : product.basePrice() > 0 ? "Buy" : "Sell";
                offers.add("<gray>" + directions + ":</gray> <white>" + escape(NpcCatalog.label(product.id())) + "</white>");
            }
        }
        if (offers.isEmpty()) offers.add("<gray>Dialogue and guidance.</gray>");
        inventory.setItem(13, icon(Material.BOOK, "<aqua>Services and stock</aqua>", offers));
        boolean room = instances.size() < maximumInstances;
        inventory.setItem(31, icon(room ? Material.LIME_CONCRETE : Material.BARRIER,
            room ? "<green><bold>Place here</bold></green>" : "<red>NPC limit reached</red>", List.of(
                "<gray>World:</gray> <white>" + escape(anchor.getWorld().getName()) + "</white>",
                "<gray>Location:</gray> <white>" + anchor.getBlockX() + ", " + anchor.getBlockY() + ", " + anchor.getBlockZ() + "</white>",
                "<gray>Uses your position and facing.</gray>",
                "<gray>Stand on a clear, solid surface.</gray>",
                "<yellow>Click to create a saved anchor.</yellow>")));
        if (room) holder.actions.put(31, target -> {
            if (holder.consumed) return;
            if (!target.getWorld().getUID().equals(anchor.getWorld().getUID())
                || target.getLocation().distanceSquared(anchor) > 1) {
                Chat.notice(target, "Your position changed. Review the refreshed placement point.");
                openCatalogEntry(target, definitionId, category, query, page);
                return;
            }
            if (placeDefinition(target, definitionId, anchor)) {
                holder.consumed = true;
                target.closeInventory();
            }
        });
        inventory.setItem(MenuKit.backSlot(54), MenuKit.back("NPC Catalog"));
        holder.actions.put(MenuKit.backSlot(54), target -> openCatalog(target, category, query, page));
        inventory.setItem(50, icon(Material.SPYGLASS, "<aqua>Refresh Placement Point</aqua>",
            MenuKit.withHint(List.of("<gray>Update to your current position and facing.</gray>"), "refresh")));
        holder.actions.put(50, target -> openCatalogEntry(target, definitionId, category, query, page));
        inventory.setItem(MenuKit.closeSlot(54), MenuKit.close());
        holder.actions.put(MenuKit.closeSlot(54), Player::closeInventory);
        player.openInventory(inventory);
    }

    private boolean createCommand(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || args.length != 2 || !content.npcs().containsKey(args[1].toLowerCase(Locale.ROOT))) {
            sender.sendRichMessage("<red>Use /npc place [definition], or browse /npc catalog.</red>");
            return true;
        }
        placeDefinition(player, args[1].toLowerCase(Locale.ROOT), player.getLocation().clone());
        return true;
    }

    private boolean placeDefinition(Player player, String definitionId, Location location) {
        if (!staffAccess(player)) return false;
        if (instances.size() >= maximumInstances) {
            Chat.error(player, "The configured NPC instance limit has been reached.");
            return false;
        }
        if (!content.npcs().containsKey(definitionId) || location.getWorld() == null
            || Bukkit.getWorld(location.getWorld().getUID()) != location.getWorld()
            || !player.getWorld().getUID().equals(location.getWorld().getUID())
            || !Double.isFinite(location.getX()) || !Double.isFinite(location.getY()) || !Double.isFinite(location.getZ())
            || !Float.isFinite(location.getYaw()) || !Float.isFinite(location.getPitch())
            || !location.getWorld().getWorldBorder().isInside(location)
            || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            Chat.error(player, "Choose a loaded, reachable placement point inside the world border.");
            return false;
        }
        try {
            NpcInstance instance = createInstance(definitionId, location);
            if (instance == null) {
                Chat.error(player, "That NPC could not be placed. Stand on a clear, solid surface; check spawn protection if it continues.");
                return false;
            }
            player.sendRichMessage("<green>Placed " + escape(content.npcs().get(definitionId).name()) + ".</green> <gray>"
                + instance.id().toString().substring(0, 8) + "</gray>");
            return true;
        } catch (RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "NPC placement failed for " + definitionId, failure);
            Chat.error(player, "NPC placement could not be saved. No new anchor was created.");
            return false;
        }
    }

    private boolean removeCommand(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendRichMessage("<red>Use /npc remove [instance].</red>");
            return true;
        }
        NpcInstance instance = resolveInstance(args[1]);
        if (instance == null) {
            sender.sendRichMessage("<red>NPC id is missing or ambiguous.</red>");
            return true;
        }
        removeLive(instance.id());
        instances.remove(instance.id());
        saveInstances();
        sender.sendRichMessage("<green>NPC anchor removed.</green>");
        return true;
    }

    private boolean moveCommand(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        NpcInstance current = args.length == 2 ? resolveInstance(args[1]) : null;
        if (player == null || current == null) {
            sender.sendRichMessage("<red>Use /npc movehere [instance].</red>");
            return true;
        }
        removeLive(current.id());
        NpcInstance moved = current.at(player.getLocation());
        instances.put(moved.id(), moved);
        saveInstances();
        spawnLive(moved);
        sender.sendRichMessage("<green>NPC anchor moved here.</green>");
        return true;
    }

    private boolean listCommand(CommandSender sender) {
        sender.sendRichMessage("<gold><bold>NPC anchors</bold></gold> <gray>(" + instances.size() + "/"
            + maximumInstances + ")</gray>");
        instances.values().forEach(instance -> sender.sendRichMessage("<yellow>"
            + instance.id().toString().substring(0, 8) + "</yellow> <white>" + instance.definitionId()
            + "</white> <gray>" + Math.round(instance.x()) + ", " + Math.round(instance.y()) + ", "
            + Math.round(instance.z()) + "</gray>"));
        return true;
    }

    private boolean reputationCommand(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendRichMessage("<red>Use /npc reputation [player] [faction] [amount].</red>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        AccountId account = target == null ? null : sessions.account(target.getUniqueId()).orElse(null);
        FactionId faction;
        int amount;
        try {
            faction = new FactionId(args[2].toLowerCase(Locale.ROOT));
            amount = Integer.parseInt(args[3]);
        } catch (IllegalArgumentException exception) {
            sender.sendRichMessage("<red>Use a known faction and a non-zero whole number.</red>");
            return true;
        }
        if (target == null || account == null || amount == 0 || !content.factions().containsKey(faction)) {
            sender.sendRichMessage("<red>Use an online player, known faction, and non-zero amount.</red>");
            return true;
        }
        reputation.adjust(account, faction, amount,
            new ReputationService.ReputationReason("admin.adjustment", "Adjusted by " + sender.getName()),
            "npc-admin-reputation:" + UUID.randomUUID()).thenAccept(result -> sync(() ->
                sender.sendRichMessage("<green>Standing is now " + result.value() + " ("
                    + signed(result.appliedAmount()) + ").</green>")));
        return true;
    }

    private void nearby(Player player) {
        List<NpcInstance> found = instances.values().stream()
            .filter(instance -> instance.worldId().equals(player.getWorld().getUID()))
            .filter(instance -> instance.location().distanceSquared(player.getLocation()) <= 1024.0D)
            .sorted(java.util.Comparator.comparingDouble(instance ->
                instance.location().distanceSquared(player.getLocation())))
            .limit(10)
            .toList();
        if (found.isEmpty()) {
            player.sendRichMessage("<gray>No NPCs are anchored within 32 blocks.</gray>");
            return;
        }
        player.sendRichMessage("<gold>Nearby NPCs</gold>");
        found.forEach(instance -> {
            NpcContent.NpcDefinition definition = content.npcs().get(instance.definitionId());
            if (definition != null) {
                player.sendRichMessage("<yellow>" + instance.id().toString().substring(0, 8) + "</yellow> "
                    + "<white>" + escape(definition.name()) + "</white> <gray>"
                    + Math.round(Math.sqrt(instance.location().distanceSquared(player.getLocation()))) + "m</gray>");
            }
        });
    }

    private NpcInstance createInstance(String definitionId, Location location) {
        NpcInstance instance = NpcInstance.create(UUID.randomUUID(), definitionId, location);
        boolean committed = false;
        try {
            if (!spawnLive(instance) || !NpcPlacementSafety.fits(liveByInstance.get(instance.id()))) return null;
            Map<UUID, NpcInstance> next = new LinkedHashMap<>(instances);
            next.put(instance.id(), instance);
            saveInstances(next);
            instances.put(instance.id(), instance);
            committed = true;
            return instance;
        } finally {
            if (!committed) {
                removeLive(instance.id());
                releaseUnusedTickets();
            }
        }
    }

    public void respawnAll() {
        removeAllLive();
        instances.values().forEach(this::spawnLive);
    }

    private boolean spawnLive(NpcInstance instance) {
        NpcContent.NpcDefinition definition = content.npcs().get(instance.definitionId());
        World world = Bukkit.getWorld(instance.worldId());
        if (definition == null || world == null) {
            return false;
        }
        Location location = instance.location();
        Chunk chunk = location.getChunk();
        chunk.addPluginChunkTicket(plugin);
        ticketedChunks.add(chunk);
        LivingEntity living;
        try {
            Class<? extends LivingEntity> type = definition.entityType().getEntityClass().asSubclass(LivingEntity.class);
            living = world.spawn(location, type, CreatureSpawnEvent.SpawnReason.CUSTOM, false,
                entity -> configureNpcBody(entity, instance.id()));
        } catch (RuntimeException failure) {
            releaseUnusedTickets();
            throw failure;
        }
        if (!living.isValid()) {
            living.remove();
            releaseUnusedTickets();
            return false;
        }
        liveByInstance.put(instance.id(), living);
        instanceByEntity.put(living.getUniqueId(), instance.id());
        try {
            Location nameplateLocation = living.getLocation().add(0.0D, living.getHeight() + 0.55D, 0.0D);
            TextDisplay nameplate = world.spawn(nameplateLocation, TextDisplay.class, display -> {
                display.text(message("<gold><bold>" + escape(definition.name()) + "</bold></gold>\n"
                    + "<gray>" + escape(definition.title()) + "</gray>"));
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setBillboard(Display.Billboard.CENTER);
                display.setSeeThrough(false);
                display.setShadowed(true);
                display.setLineWidth(180);
                display.setInvulnerable(true);
                display.setPersistent(false);
                display.setTeleportDuration(2);
            });
            if (!nameplate.isValid()) {
                nameplate.remove();
                removeLive(instance.id());
                releaseUnusedTickets();
                return false;
            }
            nameplateByInstance.put(instance.id(), nameplate);
            return true;
        } catch (RuntimeException failure) {
            removeLive(instance.id());
            releaseUnusedTickets();
            throw failure;
        }
    }

    private void configureNpcBody(LivingEntity living, UUID instanceId) {
        living.getPersistentDataContainer().set(npcInstanceKey, PersistentDataType.STRING, instanceId.toString());
        living.setAI(false);
        living.setInvulnerable(true);
        living.setSilent(true);
        living.setCollidable(false);
        living.setPersistent(false);
        living.setRemoveWhenFarAway(false);
        living.setCanPickupItems(false);
        living.customName(null);
        living.setCustomNameVisible(false);
        if (living instanceof Mob mob) {
            mob.setAware(false);
        }
        if (living instanceof Ageable ageable) {
            ageable.setAdult();
            ageable.setAgeLock(true);
        }
        if (living instanceof WanderingTrader trader) {
            trader.setDespawnDelay(Integer.MAX_VALUE);
        }
    }

    private void releaseUnusedTickets() {
        ticketedChunks.removeIf(chunk -> {
            boolean used = liveByInstance.values().stream().anyMatch(body -> body.isValid()
                && body.getWorld().getUID().equals(chunk.getWorld().getUID())
                && body.getLocation().getBlockX() >> 4 == chunk.getX()
                && body.getLocation().getBlockZ() >> 4 == chunk.getZ());
            if (!used) chunk.removePluginChunkTicket(plugin);
            return !used;
        });
    }

    private void updateGaze() {
        pruneChat();
        liveByInstance.entrySet().removeIf(entry -> !entry.getValue().isValid());
        nameplateByInstance.entrySet().removeIf(entry -> {
            if (!entry.getValue().isValid() || !liveByInstance.containsKey(entry.getKey())) {
                entry.getValue().remove();
                return true;
            }
            return false;
        });
        instanceByEntity.entrySet().removeIf(entry -> Bukkit.getEntity(entry.getKey()) == null);
        for (Map.Entry<UUID, LivingEntity> entry : liveByInstance.entrySet()) {
            NpcInstance instance = instances.get(entry.getKey());
            NpcContent.NpcDefinition definition = instance == null ? null : content.npcs().get(instance.definitionId());
            LivingEntity npc = entry.getValue();
            if (definition == null || !npc.isValid()) {
                continue;
            }
            TextDisplay nameplate = nameplateByInstance.get(entry.getKey());
            if (nameplate != null && nameplate.isValid()) {
                Location expected = npc.getLocation().add(0.0D, npc.getHeight() + 0.55D, 0.0D);
                if (!nameplate.getWorld().equals(expected.getWorld())
                    || nameplate.getLocation().distanceSquared(expected) > 0.01D) {
                    nameplate.teleport(expected);
                }
            }
            Player target = npc.getWorld().getNearbyPlayers(npc.getLocation(), definition.lookRadius()).stream()
                .min(java.util.Comparator.comparingDouble(player -> player.getLocation().distanceSquared(npc.getLocation())))
                .orElse(null);
            if (target == null) {
                continue;
            }
            Location from = npc.getEyeLocation();
            org.bukkit.util.Vector direction = target.getEyeLocation().toVector().subtract(from.toVector());
            double horizontal = Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ());
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
            float pitch = (float) Math.toDegrees(Math.atan2(-direction.getY(), horizontal));
            npc.setRotation(yaw, Math.max(-45.0F, Math.min(45.0F, pitch)));
        }
    }

    private NpcInstance instance(Entity entity) {
        UUID id = instanceByEntity.get(entity.getUniqueId());
        if (id == null) {
            String raw = entity.getPersistentDataContainer().get(npcInstanceKey, PersistentDataType.STRING);
            if (raw != null) {
                try {
                    id = UUID.fromString(raw);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return id == null ? null : instances.get(id);
    }

    private Player attacker(Entity source) {
        if (source instanceof Player player) {
            return player;
        }
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private void removeLive(UUID instanceId) {
        LivingEntity living = liveByInstance.remove(instanceId);
        if (living != null) {
            instanceByEntity.remove(living.getUniqueId());
            living.remove();
        }
        TextDisplay nameplate = nameplateByInstance.remove(instanceId);
        if (nameplate != null) {
            nameplate.remove();
        }
    }

    private void removeAllLive() {
        new ArrayList<>(liveByInstance.keySet()).forEach(this::removeLive);
        nameplateByInstance.values().forEach(Entity::remove);
        nameplateByInstance.clear();
    }

    private void loadInstances() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(instancesFile);
        ConfigurationSection root = yaml.getConfigurationSection("instances");
        if (root == null) {
            return;
        }
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(rawId);
                String definition = section.getString("definition", "");
                UUID world = UUID.fromString(section.getString("world", ""));
                instances.put(id, new NpcInstance(
                    id, definition, world,
                    section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                    (float) section.getDouble("yaw"), (float) section.getDouble("pitch")
                ));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid NPC anchor " + rawId + ": " + exception.getMessage());
            }
        }
    }

    private void saveInstances() {
        saveInstances(instances);
    }

    private void saveInstances(Map<UUID, NpcInstance> saved) {
        YamlConfiguration yaml = new YamlConfiguration();
        saved.values().stream().sorted(java.util.Comparator.comparing(instance -> instance.id().toString()))
            .forEach(instance -> {
                String path = "instances." + instance.id();
                yaml.set(path + ".definition", instance.definitionId());
                yaml.set(path + ".world", instance.worldId().toString());
                yaml.set(path + ".x", instance.x());
                yaml.set(path + ".y", instance.y());
                yaml.set(path + ".z", instance.z());
                yaml.set(path + ".yaw", instance.yaw());
                yaml.set(path + ".pitch", instance.pitch());
            });
        try {
            AtomicYamlFiles.save(yaml, instancesFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist NPC anchors", exception);
        }
    }

    private NpcInstance resolveInstance(String prefix) {
        String normalized = prefix.replace("-", "").toLowerCase(Locale.ROOT);
        List<NpcInstance> matches = instances.values().stream()
            .filter(instance -> instance.id().toString().replace("-", "").startsWith(normalized))
            .limit(2).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private ItemStack reputationIcon(FactionId faction, int points) {
        NpcContent.ReputationView view = content.reputation(faction, points);
        NpcContent.TierDefinition next = view.faction().tiers().stream()
            .filter(tier -> tier.threshold() > points).findFirst().orElse(null);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Rank:</gray> <" + view.tier().color() + ">" + escape(view.tier().name())
            + "</" + view.tier().color() + ">");
        lore.add("<gray>Reputation:</gray> <white>" + points + "</white>");
        if (next == null) {
            lore.add("<gold>[##########]</gold> <gray>Highest rank</gray>");
        } else {
            int earned = Math.max(0, points - view.tier().threshold());
            int needed = Math.max(1, next.threshold() - view.tier().threshold());
            lore.add(progressBar(earned, needed, 10) + " <gray>" + earned + "/" + needed + "</gray>");
            lore.add("<gray>Next:</gray> <" + next.color() + ">" + escape(next.name()) + "</" + next.color()
                + "> <dark_gray>at " + next.threshold() + "</dark_gray>");
        }
        lore.add("<dark_gray>--------------------</dark_gray>");
        lore.add("<gray>Vendor prices:</gray> <white>"
            + Math.round(view.tier().shopPriceMultiplier() * 100.0D) + "% of base</white>");
        return icon(Material.FILLED_MAP, "<aqua><bold>" + escape(view.faction().name()) + "</bold></aqua>", lore);
    }

    private ItemStack contractIcon(ReputationContractService.ContractView view, Instant now) {
        ReputationContract contract = view.contract();
        String color = switch (contract.cadence()) {
            case DAILY -> "yellow";
            case WEEKLY -> "aqua";
            case MONTHLY -> "light_purple";
            case STORY -> "gold";
        };
        List<String> lore = new ArrayList<>();
        wrap(contract.description(), 40).forEach(line -> lore.add("<gray>" + escape(line) + "</gray>"));
        lore.add("<dark_gray>------------------------</dark_gray>");
        lore.add("<gray>Objective:</gray> <white>" + actionLabel(contract.action()) + "</white>");
        lore.add(progressBar(view.progress(), contract.target(), 12) + " <white>" + view.progress()
            + "/" + contract.target() + "</white>");
        lore.add("<gray>Rewards:</gray> <aqua>+" + contract.reputationReward() + " reputation</aqua>"
            + (contract.markReward() > 0L ? " <gold>+" + Chat.marks(contract.markReward()) + "</gold>" : ""));
        if (contract.cadence() == ReputationContract.Cadence.STORY) {
            lore.add("<gold>One-time faction story</gold>");
        } else {
            lore.add("<gray>Resets:</gray> <white>" + until(view.period().endsAt(), now) + "</white> <dark_gray>UTC</dark_gray>");
        }
        if (contract.minimumAdventurerLevel() > 1) {
            lore.add("<gray>Required:</gray> <white>Adventurer Level "
                + contract.minimumAdventurerLevel() + "</white>");
        }
        lore.add("<dark_gray>------------------------</dark_gray>");
        switch (view.status()) {
            case LOCKED -> lore.add("<red>Reach the required level to accept.</red>");
            case AVAILABLE -> lore.add("<yellow>Click to accept this contract.</yellow>");
            case ACTIVE -> lore.add("<aqua>Active. Progress is tracked automatically.</aqua>");
            case READY -> lore.add("<green><bold>COMPLETE</bold> - Click to claim.</green>");
            case CLAIMED -> lore.add("<gray>Claimed for this period.</gray>");
        }
        Material material = view.status() == ReputationContractService.ContractStatus.LOCKED
            ? Material.BARRIER : contractMaterial(contract.action());
        String label = contract.cadence() == ReputationContract.Cadence.STORY && !contract.chapter().isBlank()
            ? "STORY " + contract.chapter() : contract.cadence().label().toUpperCase(Locale.ROOT);
        ItemStack item = icon(material, "<" + color + "><bold>[" + label
            + "]</bold></" + color + "> <white>" + escape(contract.title()) + "</white>", lore);
        if (view.status() == ReputationContractService.ContractStatus.READY) {
            ItemMeta meta = item.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material contractMaterial(QuestAction action) {
        return switch (action) {
            case MINE_ORE -> Material.IRON_PICKAXE;
            case CHOP_LOG -> Material.IRON_AXE;
            case SLAY_MONSTER -> Material.IRON_SWORD;
            case CRAFT_ITEM -> Material.CRAFTING_TABLE;
            case CATCH_FISH -> Material.FISHING_ROD;
        };
    }

    private String actionLabel(QuestAction action) {
        return switch (action) {
            case MINE_ORE -> "Mine natural ore";
            case CHOP_LOG -> "Chop natural logs";
            case SLAY_MONSTER -> "Defeat hostile creatures";
            case CRAFT_ITEM -> "Craft items";
            case CATCH_FISH -> "Catch fish";
        };
    }

    private String progressBar(int current, int target, int width) {
        int filled = target <= 0 ? width : (int) Math.floor(Math.min(1.0D,
            Math.max(0.0D, current / (double) target)) * width);
        return "<green>[" + "#".repeat(filled) + "</green><dark_gray>" + "-".repeat(width - filled)
            + "]</dark_gray>";
    }

    private String until(Instant end, Instant now) {
        Duration left = Duration.between(now, end);
        if (left.isNegative() || left.isZero()) {
            return "now";
        }
        long totalMinutes = Math.max(1L, (left.toSeconds() + 59L) / 60L);
        long days = totalMinutes / 1_440L;
        long hours = totalMinutes % 1_440L / 60L;
        long minutes = totalMinutes % 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private String shortDuration(Duration duration) {
        long minutes = Math.max(1L, duration.toMinutes());
        if (minutes % 1_440L == 0L) {
            return minutes / 1_440L + "d";
        }
        if (minutes % 60L == 0L) {
            return minutes / 60L + "h";
        }
        return minutes + "m";
    }

    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (!line.isEmpty() && line.length() + word.length() + 1 > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines.isEmpty() ? List.of("") : List.copyOf(lines);
    }

    private ItemStack productIcon(NpcContent.ShopProduct product, boolean unlocked, List<String> lore) {
        ItemStack result = unlocked ? ShopItems.create(items, product, product.amount()) : new ItemStack(Material.BARRIER);
        styleIcon(result,
            (unlocked ? "<white>" : "<gray>") + escape(title(product.id()))
                + (unlocked ? "</white>" : "</gray>"), lore);
        result.setAmount(unlocked ? product.amount() : 1);
        return result;
    }

    private void decorate(Inventory inventory) {
        ItemStack filler = icon(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of());
        ItemStack accent = icon(Material.PURPLE_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, slot < 9 && slot % 2 == 1 ? accent : filler);
        }
    }

    private void decorateConversation(Inventory inventory) {
        decorate(inventory);
        ItemStack divider = icon(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of());
        ItemStack accent = icon(Material.PURPLE_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of());
        for (int slot = 18; slot <= 26; slot++) {
            inventory.setItem(slot, slot == 22 ? accent : divider);
        }
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        styleIcon(item, name, lore);
        return item;
    }

    private void styleIcon(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        meta.itemName(message(name));
        meta.displayName(null);
        meta.lore(lore.stream().map(this::message).toList());
        meta.removeEnchantments();
        meta.setAttributeModifiers(HashMultimap.create());
        meta.addItemFlags(ItemFlag.values());
        meta.setEnchantmentGlintOverride(false);
        item.setItemMeta(meta);
    }

    private Component message(String markup) {
        return miniMessage.deserialize(markup).decoration(TextDecoration.ITALIC, false);
    }

    private String resolveDialogue(String line, Player player, DialogueContext context) {
        return line
            .replace("{player}", Chat.escape(player.getName())) // dialogue is MiniMessage; never let a name become a tag
            .replace("{reputation}", Integer.toString(context.reputation()))
            .replace("{marks}", Long.toString(context.balance()))
            .replace("{adventurer_level}", Integer.toString(context.adventurerLevel()));
    }

    private void showFaction(Player player, FactionId faction, int points) {
        NpcContent.ReputationView view = content.reputation(faction, points);
        player.sendRichMessage("<aqua>• " + escape(view.faction().name()) + "</aqua> <"
            + view.tier().color() + ">" + escape(view.tier().name()) + "</" + view.tier().color()
            + "> <white>" + points + "</white> <gray>• shop rate "
            + Math.round(view.tier().shopPriceMultiplier() * 100.0D) + "%</gray>");
    }

    private AccountId account(Player player) {
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        if (account == null) {
            Chat.accountLoading(player);
        }
        return account;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        Chat.playersOnly(sender);
        return null;
    }

    private String choiceKey(String npc, String node, String choice) {
        return npc + "/" + node + "/" + choice;
    }

    private String signed(int amount) {
        return amount > 0 ? "+" + amount : Integer.toString(amount);
    }

    private String title(String raw) {
        StringBuilder result = new StringBuilder();
        for (String word : raw.split("[_-]")) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "unknown error" : current.getMessage();
    }

    private String escape(String input) {
        return input.replace("<", "\\<").replace(">", "\\>");
    }

    private void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public void close() {
        if (gazeTask != null) {
            gazeTask.cancel();
            gazeTask = null;
        }
        removeAllLive();
        ticketedChunks.forEach(chunk -> chunk.removePluginChunkTicket(plugin));
        ticketedChunks.clear();
        conversationTokens.clear();
        clearChat();
        dialoguePending.clear();
        saveInstances();
        progress.close();
        shopLedger.close();
    }

    record DialogueContext(
        AccountId account,
        int reputation,
        long balance,
        int adventurerLevel,
        NpcProgressStore.Snapshot progress
    ) {
    }

    record ChoiceAccess(boolean available, List<String> reasons) {
        ChoiceAccess {
            reasons = List.copyOf(reasons);
        }
    }

    private record ChoiceOutcome(String shopId, ServiceAction service, String command) {
    }

    private record NpcViewRequest(UUID token, AccountId account, NpcInstance instance, Inventory source) {
    }

    private record ChatConversation(Player player, AccountId account, NpcInstance instance, NpcContent source,
                                    NpcContent.NpcDefinition npc, NpcContent.DialogueNode node, UUID token,
                                    AtomicBoolean rendered) { }

    private enum ServiceAction {
        NONE,
        BANK,
        IDENTIFIER,
        REPAIR,
        CONTRACTS,
        ARCHIVE,
        FISHERY,
        ALCHEMY,
        FORGE,
        ENCHANTMENTS
    }

    private enum PurchaseStatus {
        PAID,
        INSUFFICIENT,
        LOCKED
    }

    private record PurchaseResult(PurchaseStatus status, long price) {
    }

    public record NpcInstance(
        UUID id,
        String definitionId,
        UUID worldId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
    ) {
        static NpcInstance create(UUID id, String definitionId, Location location) {
            return new NpcInstance(id, definitionId, location.getWorld().getUID(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        }

        Location location() {
            World world = Bukkit.getWorld(worldId);
            return world == null ? null : new Location(world, x, y, z, yaw, pitch);
        }

        NpcInstance at(Location location) {
            return create(id, definitionId, location);
        }
    }

    private record ShopAccess(NpcContent source, NpcContent.NpcDefinition merchant, NpcContent.ShopDefinition shop,
                              NpcInstance returnInstance, String returnNode, String query, int directoryPage) { }

    private record ShopViewRequest(UUID token, AccountId account, ShopAccess access, Inventory source) { }

    private static final class NpcInventory implements InventoryHolder {
        private final UUID playerId;
        private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        private final Map<Integer, Consumer<Player>> secondaryActions = new HashMap<>();
        private boolean secondaryFallsBack = true;
        private AccountId staffAccount;
        private NpcContent staffContent;
        private AccountId browseAccount;
        private NpcContent browseContent;
        private ShopAccess shopAccess;
        private boolean actionPending;
        private boolean consumed;
        private UUID conversationToken;
        private Inventory inventory;

        private NpcInventory(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
