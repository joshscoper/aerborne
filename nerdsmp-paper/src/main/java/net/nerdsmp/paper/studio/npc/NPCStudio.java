package net.nerdsmp.paper.studio.npc;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.studio.LootStudioPolicy;
import net.nerdsmp.paper.studio.StudioUi;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import static net.nerdsmp.paper.studio.npc.NpcBlueprint.*;

/** Account-bound private graphs and inert branching previews; publishing never places NPCs. */
public final class NPCStudio implements Listener, AutoCloseable {
    public static final String PERMISSION = "nerdsmp.npc.admin";
    private final NerdSmpPlugin plugin; private final StudioUi ui; private final NpcEngine engine;
    private final Map<UUID, Draft> drafts = new HashMap<>(); private boolean closed;
    public NPCStudio(NerdSmpPlugin plugin, StudioUi ui) { this.plugin = plugin; this.ui = ui; engine = plugin.npcEngine(); }
    public void open(Player player) { catalog(player, 0); }
    public boolean command(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) { Chat.error(sender, "You do not have permission to author NPCs."); return true; }
        if (!(sender instanceof Player player)) { Chat.error(sender, "Open " + Chat.command("npcstudio") + " in game. NPC placement remains <white>/npc place &lt;id&gt;</white>."); return true; }
        run(player, () -> {
            if (args.length == 0 || args.length == 1 && args[0].equalsIgnoreCase("browse")) open(player);
            else if (args.length == 2 && args[0].equalsIgnoreCase("create")) begin(player, engine.draft(args[1]), null);
            else if (args.length == 2 && args[0].equalsIgnoreCase("edit")) { var npc = engine.find(args[1]).orElseThrow(() -> new IllegalArgumentException("That is not an authored NPC. Copy built-in NPCs to a new ID.")); begin(player, npc, npc); }
            else if (args.length == 3 && args[0].equalsIgnoreCase("copy")) begin(player, engine.copy(args[2], args[1]), null);
            else if (args.length == 1 && args[0].equalsIgnoreCase("resume")) show(player, require(player));
            else if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) discard(player, require(player));
            else Chat.usage(player, "/npcstudio [browse|create <id>|edit <id>|copy <source> <id>|resume|cancel]");
        }); return true;
    }
    public List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION) || args.length == 0) return List.of();
        List<String> ids = args.length == 1 ? List.of("browse", "create", "edit", "copy", "resume", "cancel")
            : args.length == 2 && args[0].equalsIgnoreCase("copy") ? plugin.npcs().definitionIds().stream().sorted().toList()
            : args.length == 2 && args[0].equalsIgnoreCase("edit") ? engine.all().stream().map(NpcBlueprint::id).toList() : List.of();
        return ids.stream().filter(id -> id.startsWith(args[args.length - 1].toLowerCase(java.util.Locale.ROOT))).limit(128).toList();
    }
    private void catalog(Player player, int requested) {
        if (!allowed(player)) return;
        List<String> all = plugin.npcs().definitionIds().stream().sorted().toList(), visible = LootStudioPolicy.entries(all, requested);
        int page = LootStudioPolicy.page(requested, all.size()); List<StudioUi.Button> buttons = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) {
            String id = visible.get(i); var definition = plugin.npcs().content().npcs().get(id); boolean authored = engine.find(id).isPresent();
            buttons.add(button(StudioUi.CONTENT_SLOTS[i], Material.VILLAGER_SPAWN_EGG, definition.name(), List.of(id, definition.title(), authored ? "Authored NPC: click to edit." : "Built-in NPC: inspect or copy."), p -> {
                var existing = engine.find(id); if (existing.isPresent()) begin(p, existing.get(), existing.get()); else inspect(p, id);
            }));
        }
        pagination(buttons, page, all.size(), p -> catalog(player, p));
        buttons.add(button(45, Material.ARROW, "Content Studio", List.of(), ui::home));
        buttons.add(button(49, Material.WRITABLE_BOOK, "New NPC", List.of("Start with a greeting and safe service options.", "Publishing does not place a body."), p -> ui.ask(p, PERMISSION, "Enter an NPC ID (up to 32 lowercase characters):", id -> begin(p, engine.draft(id.strip()), null))));
        Draft current = drafts.get(player.getUniqueId()); if (current != null && valid(player, current)) buttons.add(button(50, Material.BOOK, "Resume " + current.value.id(), List.of("Continue your private draft."), p -> show(p, current)));
        buttons.add(button(51, Material.COMPASS, "Placement Catalog", List.of("Select a published NPC to place manually.", "Placement remains a separate staff action."), p -> plugin.npcs().handleNpcCommand(p, new String[]{"catalog"})));
        ui.open(player, PERMISSION, "NPC Library", buttons);
    }
    private void inspect(Player player, String id) {
        var npc = plugin.npcs().content().npcs().get(id);
        ui.open(player, PERMISSION, "Built-in NPC", List.of(
            button(22, Material.BOOK, npc.name(), List.of(id, npc.title(), "Body: " + npc.entityType(), "Faction: " + npc.factionId().value(), "Shop: " + (npc.shopId() == null ? "none" : npc.shopId()), "Built-in definitions are read-only."), p -> { }),
            button(30, Material.WRITABLE_BOOK, "Copy to New NPC", List.of("Copy navigation and safe services only.", "Reward, command and flag choices are omitted.", "No placement anchors are copied."), p -> ui.ask(p, PERMISSION, "Enter the new NPC ID:", next -> begin(p, engine.copy(next.strip(), id), null))),
            button(45, Material.ARROW, "NPC Library", List.of(), p -> catalog(p, 0))));
    }
    private void begin(Player player, NpcBlueprint next, NpcBlueprint expected) {
        if (!allowed(player)) return;
        if (expected == null && plugin.npcs().definitionIds().contains(next.id())) throw new IllegalArgumentException("That NPC ID already exists. Edit it or choose a new ID.");
        engine.validateDraft(next); Draft old = drafts.get(player.getUniqueId());
        Consumer<Player> install = p -> {
            if (old != null && drafts.get(p.getUniqueId()) != old) throw new IllegalStateException("Your active NPC draft changed. Reopen the editor.");
            if (!drafts.containsKey(p.getUniqueId()) && drafts.size() >= 64) throw new IllegalStateException("The NPC editor is busy. Try again shortly.");
            Draft draft = new Draft(p, account(p), next, expected); drafts.put(p.getUniqueId(), draft); show(p, draft);
        };
        if (old != null && valid(player, old) && !java.util.Objects.equals(old.value, old.expected)) ui.confirm(player, PERMISSION, "Replace Unpublished NPC Draft?", List.of("Your draft for " + old.value.id() + " will be discarded.", "Published NPCs and placements remain unchanged."), install);
        else install.accept(player);
    }
    private void show(Player player, Draft draft) {
        if (!valid(player, draft)) return; NpcBlueprint npc = draft.value; List<StudioUi.Button> buttons = new ArrayList<>();
        buttons.add(button(13, Material.VILLAGER_SPAWN_EGG, npc.name(), List.of(npc.title(), npc.id(), "Faction: " + npc.faction(), "Body: " + npc.type(), "Dialogue nodes: " + npc.nodes().size()), p -> { }));
        buttons.add(button(20, Material.NAME_TAG, "Name", List.of(npc.name()), p -> prompt(p, draft, "Enter the NPC's name:", next -> NpcStudioPolicy.details(draft.value, next, draft.value.title(), draft.value.type(), draft.value.faction(), draft.value.shop(), draft.value.lookRadius()))));
        buttons.add(button(21, Material.PAPER, "Title", List.of(npc.title().isEmpty() ? "No title" : npc.title(), "Enter - to clear."), p -> prompt(p, draft, "Enter the NPC's title, or - to clear:", next -> NpcStudioPolicy.details(draft.value, draft.value.name(), next.equals("-") ? "" : next, draft.value.type(), draft.value.faction(), draft.value.shop(), draft.value.lookRadius()))));
        buttons.add(button(22, Material.ARMOR_STAND, "Body", List.of(npc.type(), draft.expected == null ? "Choose a vanilla body type." : "Published body types are locked."), p -> select(p, draft, "NPC Body", TYPES.stream().sorted().toList(), next -> NpcStudioPolicy.details(draft.value, draft.value.name(), draft.value.title(), next, draft.value.faction(), draft.value.shop(), draft.value.lookRadius()), 0)));
        buttons.add(button(23, Material.WHITE_BANNER, "Faction", List.of(npc.faction(), "Contracts use this existing faction."), p -> select(p, draft, "NPC Faction", engine.baseContent().factions().keySet().stream().map(net.nerdsmp.api.model.FactionId::value).sorted().toList(), next -> NpcStudioPolicy.details(draft.value, draft.value.name(), draft.value.title(), draft.value.type(), next, draft.value.shop(), draft.value.lookRadius()), 0)));
        buttons.add(button(24, Material.EMERALD, "Shop", List.of(npc.shop().isEmpty() ? "No shop" : npc.shop(), "Choose an existing shop definition."), p -> {
            var shops = new ArrayList<String>(); shops.add("none"); engine.baseContent().shops().keySet().stream().sorted().forEach(shops::add);
            select(p, draft, "NPC Shop", shops, next -> NpcStudioPolicy.details(draft.value, draft.value.name(), draft.value.title(), draft.value.type(), draft.value.faction(), next.equals("none") ? "" : next, draft.value.lookRadius()), 0);
        }));
        buttons.add(button(25, Material.ENDER_EYE, "Look Radius", List.of(npc.lookRadius() + " blocks", "Allowed range: 2 to 24."), p -> prompt(p, draft, "Enter look radius from 2 to 24:", next -> {
            double radius; try { radius = Double.parseDouble(next); } catch (NumberFormatException failure) { throw new IllegalArgumentException("Enter a numeric radius."); }
            return NpcStudioPolicy.details(draft.value, draft.value.name(), draft.value.title(), draft.value.type(), draft.value.faction(), draft.value.shop(), radius);
        })));
        buttons.add(button(29, Material.COMPARATOR, "Root Dialogue", List.of(npc.root(), "The first node players see."), p -> select(p, draft, "Root Dialogue", NpcStudioPolicy.nodeIds(draft.value), next -> NpcStudioPolicy.root(draft.value, next), 0)));
        buttons.add(button(31, Material.WRITABLE_BOOK, "Dialogue Nodes", List.of("Edit text, branching responses and services.", "Up to 32 nodes and five responses per node."), p -> nodes(p, draft, 0)));
        buttons.add(button(33, Material.SPYGLASS, "Conversation Preview", List.of("Walk through the dialogue without effects.", "No services, rewards or NPC bodies are created."), p -> preview(p, draft, draft.value.root())));
        buttons.add(button(40, Material.LIME_CONCRETE, "Publish NPC", List.of("Save and activate this definition.", "Refreshes only this NPC's existing anchors.", "Does not create a placement."), p -> publish(p, draft)));
        buttons.add(button(42, Material.BOOK, "Copy Draft", List.of("Start a new NPC from these definitions.", "No placement anchors are copied."), p -> { long revision = draft.revision; ui.ask(p, PERMISSION, "Enter the copied NPC's ID:", next -> { if (same(p, draft, revision)) begin(p, NpcBlueprints.copy(next.strip(), draft.value), null); }); }));
        buttons.add(button(45, Material.ARROW, "NPC Library", List.of("Your draft remains available."), p -> catalog(p, 0)));
        buttons.add(button(53, Material.BARRIER, "Discard Draft", List.of("Published content remains unchanged."), p -> discard(p, draft)));
        ui.open(player, PERMISSION, "NPC Draft: " + npc.id(), buttons);
    }
    private void select(Player player, Draft draft, String title, List<String> options, java.util.function.Function<String, NpcBlueprint> edit, int requested) {
        if (!valid(player, draft)) return; long revision = draft.revision; int page = LootStudioPolicy.page(requested, options.size());
        List<String> visible = LootStudioPolicy.entries(options, page); List<StudioUi.Button> buttons = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) { String next = visible.get(i); buttons.add(button(StudioUi.CONTENT_SLOTS[i], Material.PAPER, next, List.of("Choose this setting."), p -> { if (same(p, draft, revision)) { update(draft, edit.apply(next)); show(p, draft); } })); }
        pagination(buttons, page, options.size(), p -> select(player, draft, title, options, edit, p));
        buttons.add(button(45, Material.ARROW, "NPC Draft", List.of(), p -> show(p, draft))); ui.open(player, PERMISSION, title, buttons);
    }
    private void prompt(Player player, Draft draft, String question, java.util.function.Function<String, NpcBlueprint> edit) {
        long revision = draft.revision; ui.ask(player, PERMISSION, question, next -> { if (same(player, draft, revision)) { update(draft, edit.apply(next.strip())); show(player, draft); } });
    }
    private void nodes(Player player, Draft draft, int requested) {
        if (!valid(player, draft)) return; List<String> all = NpcStudioPolicy.nodeIds(draft.value), visible = LootStudioPolicy.entries(all, requested);
        int page = LootStudioPolicy.page(requested, all.size()); List<StudioUi.Button> buttons = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) { String id = visible.get(i); Node node = draft.value.nodes().get(id); buttons.add(button(StudioUi.CONTENT_SLOTS[i], Material.WRITABLE_BOOK, id, List.of(node.text().getFirst(), node.choices().size() + " responses", id.equals(draft.value.root()) ? "Root dialogue" : "Branch dialogue"), p -> node(p, draft, id))); }
        pagination(buttons, page, all.size(), p -> nodes(player, draft, p));
        buttons.add(button(45, Material.ARROW, "NPC Draft", List.of(), p -> show(p, draft)));
        buttons.add(button(49, Material.EMERALD, "Add Dialogue Node", List.of("Connect it with a response before publishing."), p -> { long revision = draft.revision; ui.ask(p, PERMISSION, "Enter a local node ID (up to 24 lowercase characters):", id -> { if (same(p, draft, revision)) {
            String key = identifier(id.strip(), 24); update(draft, NpcStudioPolicy.node(draft.value, new Node(key, List.of("What would you like to know?"), List.of(new Choice("goodbye", "I'll be going.", "", Service.CLOSE, 1, -1000))), false)); node(p, draft, key);
        }}); }));
        ui.open(player, PERMISSION, "Dialogue Nodes", buttons);
    }
    private void node(Player player, Draft draft, String id) {
        if (!valid(player, draft)) return; Node node = requireNode(draft, id); List<StudioUi.Button> buttons = new ArrayList<>();
        buttons.add(button(13, Material.BOOK, "Dialogue Text", node.text(), p -> { long revision = draft.revision; ui.ask(p, PERMISSION, "Enter dialogue text; separate up to eight lines with |:", text -> { if (same(p, draft, revision)) { update(draft, NpcStudioPolicy.node(draft.value, NpcStudioPolicy.text(node, text), true)); node(p, draft, id); } }); }));
        for (int i = 0; i < node.choices().size(); i++) { Choice choice = node.choices().get(i); buttons.add(button(20 + i, Material.PAPER, choice.text(), List.of(choice.id(), target(choice), "Requires level " + choice.minimumLevel() + ", reputation " + choice.minimumReputation(), "Click to edit this response."), p -> choice(p, draft, id, choice.id()))); }
        buttons.add(button(31, Material.EMERALD, "Add Response", List.of(node.choices().size() + " / 5 used", "Create a branch or safe service option."), p -> { long revision = draft.revision; ui.ask(p, PERMISSION, "Enter a unique response ID (up to 24 lowercase characters):", next -> { if (same(p, draft, revision)) {
            String key = identifier(next.strip(), 24); Choice added = new Choice(key, NpcBlueprints.readable(key), "", Service.CLOSE, 1, -1000);
            update(draft, NpcStudioPolicy.node(draft.value, NpcStudioPolicy.choice(node, added, false), true)); choice(p, draft, id, key);
        }}); }));
        buttons.add(button(33, Material.BARRIER, "Remove Node", List.of("The root or a referenced branch cannot be removed."), p -> { long revision = draft.revision; ui.confirm(p, PERMISSION, "Remove Dialogue Node?", List.of(id, "Only this draft is changed."), actor -> { if (same(actor, draft, revision)) { update(draft, NpcStudioPolicy.removeNode(draft.value, id)); nodes(actor, draft, 0); } }); }));
        buttons.add(button(45, Material.ARROW, "Dialogue Nodes", List.of(), p -> nodes(p, draft, 0))); ui.open(player, PERMISSION, "Dialogue: " + id, buttons);
    }
    private void choice(Player player, Draft draft, String nodeId, String choiceId) {
        if (!valid(player, draft)) return; Node node = requireNode(draft, nodeId); Choice choice = node.choices().stream().filter(value -> value.id().equals(choiceId)).findFirst().orElseThrow(); List<StudioUi.Button> buttons = new ArrayList<>();
        buttons.add(button(13, Material.BOOK, choice.text(), List.of(target(choice), "Requires level " + choice.minimumLevel() + ", reputation " + choice.minimumReputation()), p -> { }));
        buttons.add(button(20, Material.NAME_TAG, "Response Text", List.of(choice.text()), p -> { long revision = draft.revision; ui.ask(p, PERMISSION, "Enter the player response text:", text -> { if (same(p, draft, revision)) { update(draft, NpcStudioPolicy.node(draft.value, NpcStudioPolicy.choice(node, NpcStudioPolicy.label(choice, text), true), true)); choice(p, draft, nodeId, choiceId); } }); }));
        buttons.add(button(22, Material.LEAD, "Destination or Service", List.of(target(choice), "Choose another node or a terminal service."), p -> destinations(p, draft, nodeId, choiceId, 0)));
        buttons.add(button(24, Material.EXPERIENCE_BOTTLE, "Requirements", List.of("Minimum level: " + choice.minimumLevel(), "Minimum reputation: " + choice.minimumReputation(), "No currency or story-flag checks are authored here."), p -> { long revision = draft.revision; ui.ask(p, PERMISSION, "Enter minimum level and reputation (example: 10 100; default: 1 -1000):", text -> { if (same(p, draft, revision)) { update(draft, NpcStudioPolicy.node(draft.value, NpcStudioPolicy.choice(node, NpcStudioPolicy.requirements(choice, text), true), true)); choice(p, draft, nodeId, choiceId); } }); }));
        buttons.add(button(31, Material.BARRIER, "Remove Response", List.of("Publish to apply the removal."), p -> { long revision = draft.revision; ui.confirm(p, PERMISSION, "Remove Response?", List.of(choice.text()), actor -> { if (same(actor, draft, revision)) { update(draft, NpcStudioPolicy.node(draft.value, NpcStudioPolicy.removeChoice(node, choiceId), true)); node(actor, draft, nodeId); } }); }));
        buttons.add(button(45, Material.ARROW, "Back to Node", List.of(), p -> node(p, draft, nodeId))); ui.open(player, PERMISSION, "Response: " + choiceId, buttons);
    }
    private void destinations(Player player, Draft draft, String nodeId, String choiceId, int requested) {
        if (!valid(player, draft)) return; Node node = requireNode(draft, nodeId); Choice choice = node.choices().stream().filter(value -> value.id().equals(choiceId)).findFirst().orElseThrow();
        List<String> all = new ArrayList<>(); NpcStudioPolicy.nodeIds(draft.value).forEach(id -> all.add("node:" + id)); Arrays.stream(Service.values()).filter(service -> service != Service.NONE).forEach(service -> all.add("service:" + service.name()));
        int page = LootStudioPolicy.page(requested, all.size()); List<String> visible = LootStudioPolicy.entries(all, page); List<StudioUi.Button> buttons = new ArrayList<>(); long revision = draft.revision;
        for (int i = 0; i < visible.size(); i++) { String target = visible.get(i); buttons.add(button(StudioUi.CONTENT_SLOTS[i], target.startsWith("node:") ? Material.PAPER : Material.COMPASS, target, List.of(target.equals("service:CONTRACTS") ? "Existing faction contracts and rewards." : "Navigation/service only; no editor reward grants."), p -> { if (same(p, draft, revision)) {
            boolean branch = target.startsWith("node:"); Choice next = NpcStudioPolicy.target(choice, branch ? target.substring(5) : "", branch ? Service.NONE : Service.valueOf(target.substring(8)));
            update(draft, NpcStudioPolicy.node(draft.value, NpcStudioPolicy.choice(node, next, true), true)); choice(p, draft, nodeId, choiceId);
        }})); }
        pagination(buttons, page, all.size(), p -> destinations(player, draft, nodeId, choiceId, p)); buttons.add(button(45, Material.ARROW, "Back to Response", List.of(), p -> choice(p, draft, nodeId, choiceId))); ui.open(player, PERMISSION, "Response Destination", buttons);
    }
    private void preview(Player player, Draft draft, String nodeId) {
        if (!valid(player, draft)) return; Node node = requireNode(draft, nodeId); List<StudioUi.Button> buttons = new ArrayList<>();
        buttons.add(button(13, Material.BOOK, draft.value.name() + " - " + nodeId, node.text(), p -> { }));
        for (int i = 0; i < node.choices().size(); i++) { Choice choice = node.choices().get(i); buttons.add(button(20 + i, Material.PAPER, choice.text(), List.of(target(choice), "Requires level " + choice.minimumLevel() + ", reputation " + choice.minimumReputation(), "Preview ignores gates and does not run effects."), p -> {
            if (!choice.next().isEmpty()) preview(p, draft, choice.next()); else ui.open(p, PERMISSION, "Service Preview", List.of(button(22, Material.COMPASS, NpcBlueprints.readable(choice.service().name().toLowerCase(java.util.Locale.ROOT)), List.of("This response would " + (choice.service() == Service.CLOSE ? "end the conversation." : "open the existing service."), "No live service is executed in preview."), actor -> { }), button(45, Material.ARROW, "Back to Conversation", List.of(), actor -> preview(actor, draft, nodeId))));
        })); }
        buttons.add(button(45, Material.ARROW, "NPC Draft", List.of(), p -> show(p, draft))); ui.open(player, PERMISSION, "Conversation Preview", buttons);
    }
    private void publish(Player player, Draft draft) {
        if (!valid(player, draft)) return; long revision = draft.revision; NpcBlueprint snapshot = draft.value; snapshot.validatePublish();
        ui.confirm(player, PERMISSION, "Publish " + snapshot.id() + "?", List.of(snapshot.name(), "Faction: " + snapshot.faction(), "Dialogue nodes: " + snapshot.nodes().size(), "Existing placements of this NPC refresh.", "No new NPC anchor or body is created."), actor -> run(actor, () -> { if (same(actor, draft, revision)) {
            engine.publish(snapshot, draft.expected); draft.expected = snapshot; draft.revision++; Chat.success(actor, "Published <white>" + Chat.escape(snapshot.id()) + "</white>. Place it manually with " + Chat.command("npc place " + snapshot.id()) + "."); show(actor, draft);
        }}));
    }
    private void discard(Player player, Draft draft) { long revision = draft.revision; ui.confirm(player, PERMISSION, "Discard NPC Draft?", List.of("Published NPCs and placements remain unchanged."), actor -> { if (same(actor, draft, revision)) { drafts.remove(actor.getUniqueId(), draft); catalog(actor, 0); } }); }
    private void update(Draft draft, NpcBlueprint next) { engine.validateDraft(next); draft.value = next; draft.revision++; }
    private Node requireNode(Draft draft, String id) { Node node = draft.value.nodes().get(id); if (node == null) throw new IllegalArgumentException("That node no longer exists."); return node; }
    private Draft require(Player player) { Draft draft = drafts.get(player.getUniqueId()); if (draft == null || !valid(player, draft)) throw new IllegalArgumentException("There is no active NPC draft to resume."); return draft; }
    private AccountId account(Player player) { return plugin.sessions().account(player.getUniqueId()).orElseThrow(() -> new IllegalStateException("Your profile is still loading.")); }
    private boolean allowed(Player player) { return !closed && player.hasPermission(PERMISSION) && player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player && plugin.sessions().account(player.getUniqueId()).isPresent(); }
    private boolean valid(Player player, Draft draft) { return allowed(player) && draft.player == player && drafts.get(player.getUniqueId()) == draft && plugin.sessions().account(player.getUniqueId()).filter(draft.account::equals).isPresent(); }
    private boolean same(Player player, Draft draft, long revision) { if (!valid(player, draft) || draft.revision != revision) { Chat.notice(player, "This draft changed. Reopen it with " + Chat.command("npcstudio resume") + "."); return false; } return true; }
    private StudioUi.Button button(int slot, Material icon, String title, List<String> lore, Consumer<Player> action) { return new StudioUi.Button(slot, StudioUi.card(icon, title, lore), p -> run(p, () -> action.accept(p))); }
    private void pagination(List<StudioUi.Button> buttons, int page, int count, Consumer<Integer> next) {
        if (page > 0) buttons.add(button(46, Material.ARROW, "Previous Page", List.of(), p -> next.accept(page - 1)));
        buttons.add(button(47, Material.PAPER, "Page " + (page + 1) + " / " + (LootStudioPolicy.page(Integer.MAX_VALUE, count) + 1), List.of(count + " entries"), p -> { }));
        if ((page + 1) * LootStudioPolicy.PAGE_SIZE < count) buttons.add(button(52, Material.ARROW, "Next Page", List.of(), p -> next.accept(page + 1)));
    }
    private static String target(Choice choice) { return choice.next().isEmpty() ? "Service: " + choice.service().name().toLowerCase(java.util.Locale.ROOT) : "Next node: " + choice.next(); }
    @FunctionalInterface private interface Action { void run() throws IOException; }
    private void run(Player player, Action action) { if (!allowed(player)) return; try { action.run(); } catch (IllegalArgumentException | IllegalStateException failure) { fail(player, failure.getMessage()); } catch (IOException failure) { plugin.getLogger().log(Level.SEVERE, "NPC Studio save failed; prior content remains active", failure); Chat.error(player, "NPC content could not be saved. Your draft is kept; check the server log."); } }
    private static void fail(CommandSender sender, String text) { Chat.error(sender, Chat.escape(text == null ? "That edit was not valid." : text)); }
    @EventHandler public void quit(PlayerQuitEvent event) { drafts.remove(event.getPlayer().getUniqueId()); }
    @Override public void close() { closed = true; drafts.clear(); }
    private static final class Draft { final Player player; final AccountId account; NpcBlueprint value, expected; long revision; Draft(Player player, AccountId account, NpcBlueprint value, NpcBlueprint expected) { this.player = player; this.account = account; this.value = value; this.expected = expected; } }
}
