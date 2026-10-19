package net.nerdsmp.smoke;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.forge.ArcaneEnchantment;
import net.nerdsmp.paper.forge.GemType;
import net.nerdsmp.paper.forge.arcane.ArcaneForge;
import net.nerdsmp.paper.forge.arcane.ArcaneSignature;
import net.nerdsmp.paper.item.PaperItemService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Real Paper item data, knowledge persistence and Arcane Forge transactions with a stand-in player. */
public final class ArcaneForgeSmoke {
    private ArcaneForgeSmoke() { }

    public static void run(NerdSmpPlugin core, World world, BiConsumer<Boolean, String> check) {
        PaperItemService items = core.items();
        NamespacedKey enchantKey = new NamespacedKey(core, "custom_enchantments");
        NamespacedKey signatureKey = new NamespacedKey(core, "arcane_signature");

        // 1. Lossless enchantment data on a real item.
        ItemStack sword = items.createCrafted(Material.IRON_SWORD);
        var meta = sword.getItemMeta();
        meta.getPersistentDataContainer().set(enchantKey, PersistentDataType.STRING, "keen_edge:2|from_future:4|scholar:x");
        sword.setItemMeta(meta);
        items.refresh(sword);
        check.accept(items.enchantments(sword).equals(java.util.Map.of(ArcaneEnchantment.KEEN_EDGE, 2)),
            "legacy reader result is unchanged for items with unreadable entries");
        check.accept(items.enchant(sword, ArcaneEnchantment.KEEN_EDGE, 3) == PaperItemService.UpgradeResult.APPLIED
            && "keen_edge:3|from_future:4|scholar:x".equals(sword.getItemMeta().getPersistentDataContainer().get(enchantKey, PersistentDataType.STRING)),
            "enchanting keeps entries this build cannot read");
        check.accept(items.isAuthentic(sword), "item schema stays authentic after Forge 2.0 writes");

        // 2. Arcane Signature data and tooltip.
        ItemStack bound = items.createCrafted(Material.IRON_SWORD);
        check.accept(items.bindArcaneSignature(bound, ArcaneSignature.STORMWAKE, 2) == PaperItemService.UpgradeResult.APPLIED
            && items.arcaneSignature(bound).orElse(null) == ArcaneSignature.STORMWAKE && items.arcaneInstability(bound) == 2,
            "signature and instability are stored on real PDC");
        String lore = plain(bound);
        check.accept(lore.contains("ARCANE SIGNATURE") && lore.contains("Stormwake") && lore.contains("Unstable 2"),
            "tooltip renders the Arcane Signature section");
        items.refresh(bound);
        check.accept(items.arcaneSignature(bound).isPresent() && items.stabilize(bound) && items.arcaneInstability(bound) == 0
            && items.arcaneSignature(bound).isPresent(), "refresh keeps the signature; stabilize clears only instability");
        check.accept(items.bindArcaneSignature(items.createCrafted(Material.IRON_PICKAXE), ArcaneSignature.STORMWAKE, 0)
            == PaperItemService.UpgradeResult.INCOMPATIBLE, "a weapon signature cannot be bound to a pickaxe");
        ItemStack dormant = items.createCrafted(Material.IRON_SWORD);
        var dormantMeta = dormant.getItemMeta();
        dormantMeta.getPersistentDataContainer().set(signatureKey, PersistentDataType.STRING, "future_signature");
        dormant.setItemMeta(dormantMeta);
        items.enchant(dormant, ArcaneEnchantment.KEEN_EDGE, 1);
        check.accept(items.arcaneSignature(dormant).isEmpty()
            && "future_signature".equals(items.arcaneSignatureId(dormant).orElse(null))
            && plain(dormant).contains("Dormant signature"),
            "an unknown signature id is kept, shown as dormant and never activates");

        // 3. Transactions with a stand-in player beside a real enchanting table.
        Fixture fixture = new Fixture(world);
        AccountId account = AccountId.random();
        core.sessions().join(fixture.id, account);
        try {
            ArcaneForge forge = ArcaneForge.fixture(core, player -> true, player -> 50);
            if (!forge.settings().research().enabled() || !forge.settings().imbuement().enabled()) {
                check.accept(!forge.settings().research().enabled() && !forge.settings().imbuement().enabled(),
                    "release profile leaves research and chosen imbuement gated");
                fixture.storage[0] = items.createCrafted(Material.IRON_SWORD);
                fixture.storage[5] = core.forge().createGem(GemType.RUBY, 1);
                fixture.storage[6] = core.enchanting().createArcaneDust(10);
                ItemStack[] before = Arrays.stream(fixture.storage).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
                String profileBefore = core.playerData().read(account, yaml -> yaml.saveToString());
                check.accept(forge.research(fixture.player, List.of(new ArcaneForge.GemChoice(GemType.RUBY, 1)), false).outcome()
                    == ArcaneForge.Outcome.DISABLED, "release research requests are explicitly refused");
                check.accept(forge.imbue(fixture.player, fixture.storage[0].clone(), 0,
                    forge.registry().of(ArcaneEnchantment.KEEN_EDGE), 1) == ArcaneForge.Outcome.DISABLED,
                    "release chosen-imbuement requests are explicitly refused");
                check.accept(Arrays.equals(before, fixture.storage)
                    && profileBefore.equals(core.playerData().read(account, yaml -> yaml.saveToString())),
                    "disabled advanced Forge requests change neither inventory nor persistent knowledge");
                check.accept(items.enchant(fixture.storage[0], ArcaneEnchantment.KEEN_EDGE, 1) == PaperItemService.UpgradeResult.APPLIED
                    && items.isAuthentic(fixture.storage[0]), "legacy enchantment writes still work with new features gated");
                core.getLogger().info("ZONE_SMOKE_ARCANE_FORGE_GATED_COMPLETE PASS");
                return;
            }
            check.accept(forge.settings().research().enabled() && forge.settings().imbuement().enabled(),
                "local test config enables research and imbuement");
            fixture.armor[2] = items.createCrafted(Material.IRON_LEGGINGS);
            items.enchant(fixture.armor[2], ArcaneEnchantment.WARDING, 1);
            var seeded = forge.profile(fixture.player).orElseThrow();
            check.accept(seeded.seeded() && seeded.known("warding") && !seeded.known("keen_edge"),
                "knowledge is seeded once from gear the player already owns");
            check.accept(!forge.atStation(fixture.player), "no table: no station");
            world.getBlockAt(fixture.at.getBlockX() + 2, fixture.at.getBlockY(), fixture.at.getBlockZ()).setType(Material.ENCHANTING_TABLE, false);
            check.accept(forge.atStation(fixture.player), "a nearby enchanting table is a station");

            // Research: Ruby I + 4 Dust discovers Keen Edge.
            fixture.storage[5] = core.forge().createGem(GemType.RUBY, 1);
            fixture.storage[6] = core.enchanting().createArcaneDust(10);
            var research = forge.research(fixture.player, List.of(new ArcaneForge.GemChoice(GemType.RUBY, 1)), false);
            check.accept(research.outcome() == ArcaneForge.Outcome.APPLIED && research.result().discovered()
                && fixture.storage[5] == null && fixture.storage[6].getAmount() == 6,
                "research consumes exactly one gem and its Dust and discovers the fundamental");
            check.accept(forge.profile(account).known("keen_edge")
                && core.playerData().read(account, yaml -> yaml.getBoolean("arcane.entries.keen_edge.discovered")),
                "discovery is persisted in the player document");
            fixture.storage[5] = core.forge().createGem(GemType.RUBY, 1);
            var repeat = forge.research(fixture.player, List.of(new ArcaneForge.GemChoice(GemType.RUBY, 1)), false);
            check.accept(repeat.outcome() != ArcaneForge.Outcome.APPLIED && fixture.storage[5] != null
                && fixture.storage[6].getAmount() == 6, "a known recipe is refused and spends nothing");

            // Chosen Imbuement.
            fixture.storage[0] = items.createCrafted(Material.IRON_SWORD);
            fixture.storage[6] = core.enchanting().createArcaneDust(64);
            ItemStack expected = fixture.storage[0].clone();
            check.accept(forge.imbue(fixture.player, expected, 0, forge.registry().of(ArcaneEnchantment.KEEN_EDGE), 2)
                == ArcaneForge.Outcome.APPLIED && items.enchantmentLevel(fixture.storage[0], ArcaneEnchantment.KEEN_EDGE) == 2
                && fixture.storage[6].getAmount() == 64 - 6 * (1 + 2), "imbuement applies the exact level for the quoted Dust");
            int dustBefore = fixture.storage[6].getAmount();
            check.accept(forge.imbue(fixture.player, expected, 0, forge.registry().of(ArcaneEnchantment.KEEN_EDGE), 3)
                == ArcaneForge.Outcome.ITEM_CHANGED && fixture.storage[6].getAmount() == dustBefore,
                "a stale reviewed item is rejected without spending");
            fixture.storage[6] = core.enchanting().createArcaneDust(5);
            ItemStack current = fixture.storage[0].clone();
            check.accept(forge.imbue(fixture.player, current, 0, forge.registry().of(ArcaneEnchantment.KEEN_EDGE), 3)
                == ArcaneForge.Outcome.MISSING_DUST && fixture.storage[6].getAmount() == 5
                && items.enchantmentLevel(fixture.storage[0], ArcaneEnchantment.KEEN_EDGE) == 2,
                "too little Dust changes neither the item nor the Dust");
            check.accept(forge.imbue(fixture.player, current, 0, forge.registry().of(ArcaneEnchantment.EXECUTIONER), 1)
                == ArcaneForge.Outcome.UNDISCOVERED, "undiscovered enchantments cannot be imbued");

            // Signature binding: capacity is enforced, and nothing is spent when it does not fit.
            if (ArcaneForge.definitionActive(forge.registry().of(ArcaneSignature.STORMWAKE), forge.settings())) {
            forge.grant(account, forge.registry().of(ArcaneSignature.STORMWAKE));
            fixture.storage[6] = core.enchanting().createArcaneDust(64);
            fixture.storage[7] = core.forge().createGem(GemType.RUBY, 3);
            fixture.storage[8] = core.forge().createGem(GemType.AQUAMARINE, 3);
            fixture.storage[9] = core.forge().createGem(GemType.EMERALD, 4);
            fixture.storage[0] = items.createCrafted(Material.IRON_SWORD);
            items.enchant(fixture.storage[0], ArcaneEnchantment.SOULSIPHON, 5);
            ItemStack full = fixture.storage[0].clone();
            check.accept(forge.bindSignature(fixture.player, full, 0, ArcaneSignature.STORMWAKE) == ArcaneForge.Outcome.OVER_CAPACITY
                && fixture.storage[7] != null && fixture.storage[6].getAmount() == 64 && items.arcaneSignature(fixture.storage[0]).isEmpty(),
                "a full legacy item keeps everything and a signature that does not fit spends nothing");
            fixture.storage[0] = items.createCrafted(Material.DIAMOND_SWORD);
            int capacity = forge.view(fixture.storage[0]).capacity();
            ItemStack empty = fixture.storage[0].clone();
            var outcome = forge.bindSignature(fixture.player, empty, 0, ArcaneSignature.STORMWAKE);
            var fit = net.nerdsmp.paper.forge.arcane.ArcaneCapacity.evaluate(capacity, 0, 0,
                forge.settings().capacity().signatureCost(), forge.settings().overcharge().enabled(),
                forge.settings().overcharge().maximumPercent());
            check.accept(fit.allowed(), "fixture item can take a signature (capacity " + capacity + ")");
            check.accept(outcome == ArcaneForge.Outcome.APPLIED && items.arcaneSignature(fixture.storage[0]).orElse(null) == ArcaneSignature.STORMWAKE
                && items.arcaneInstability(fixture.storage[0]) == fit.overcharge()
                && fixture.storage[7] == null && fixture.storage[8] == null && fixture.storage[9] == null && fixture.storage[6] == null,
                "binding spends 64 Dust and one gem of each Aspect exactly once and records any overcharge");
            } else {
                forge.grant(account, forge.registry().of(ArcaneSignature.STORMWAKE));
                fixture.storage[0] = items.createCrafted(Material.DIAMOND_SWORD);
                fixture.storage[6] = core.enchanting().createArcaneDust(64);
                ItemStack[] original = Arrays.stream(fixture.storage).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
                check.accept(forge.signatureQuote(fixture.player, forge.profile(fixture.player).orElseThrow(), fixture.storage[0], ArcaneSignature.STORMWAKE)
                    .status() == ArcaneForge.SignatureStatus.DISABLED, "release signature preview states that this runtime power is held");
                check.accept(forge.bindSignature(fixture.player, fixture.storage[0].clone(), 0, ArcaneSignature.STORMWAKE)
                    == ArcaneForge.Outcome.DISABLED && Arrays.equals(original, fixture.storage),
                    "disabled signature binding preserves every item and all Dust");
            }
            // Effect enchantments: real item data and stats; offered only while switched on.
            int expectedPool = 12 + (forge.settings().effects().bloodletter() ? 1 : 0)
                + (forge.settings().effects().veinseeker() ? 1 : 0) + (forge.settings().effects().chainHarvest() ? 1 : 0);
            check.accept(ArcaneForge.obtainable(core).length == expectedPool,
                "table, tome and merchant pools include only enabled effect enchantments");
            ItemStack pick = items.createCrafted(Material.IRON_PICKAXE);
            check.accept(items.enchant(pick, ArcaneEnchantment.VEINSEEKER, 3) == PaperItemService.UpgradeResult.APPLIED
                && items.gear(pick).orElseThrow().stat("vein-size") == (forge.settings().effects().veinseeker() ? 6.0D : 0.0D)
                && items.enchant(items.createCrafted(Material.IRON_SWORD), ArcaneEnchantment.VEINSEEKER, 1) == PaperItemService.UpgradeResult.INCOMPATIBLE,
                "Veinseeker metadata survives while its stat respects the runtime gate");
            for (var definition : forge.registry().all()) {
                if (ArcaneForge.definitionActive(definition, forge.settings())) continue;
                var choices = definition.aspects().stream().map(aspect -> new ArcaneForge.GemChoice(
                    Arrays.stream(GemType.values()).filter(type -> net.nerdsmp.paper.forge.arcane.ArcaneAspect.ofGem(type)
                        .filter(aspect::equals).isPresent()).findFirst().orElseThrow(), 1)).toList();
                for (int index = 0; index < choices.size(); index++) fixture.storage[10 + index] = core.forge().createGem(choices.get(index).type(), 1);
                fixture.storage[6] = core.enchanting().createArcaneDust(64);
                ItemStack[] original = Arrays.stream(fixture.storage).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
                String knowledge = forge.describe(account);
                var plan = forge.researchPlan(fixture.player, forge.profile(fixture.player).orElseThrow(), choices, false);
                check.accept(plan.status() == net.nerdsmp.paper.forge.arcane.ArcaneResearch.Status.FEATURE_DISABLED && plan.dustCost() == 0,
                    "disabled discipline " + definition.id() + " has an explicit zero-cost research refusal");
                check.accept(forge.research(fixture.player, choices, false).outcome() == ArcaneForge.Outcome.DISABLED
                    && Arrays.equals(original, fixture.storage) && knowledge.equals(forge.describe(account)),
                    "disabled discipline " + definition.id() + " consumes no gems, Dust or knowledge");
            }

            // Corruption: exact bonus, refusals spend nothing, cleansing restores the item.
            fixture.storage[0] = items.createCrafted(Material.IRON_SWORD);
            fixture.storage[6] = core.enchanting().createArcaneDust(64);
            double before = items.gear(fixture.storage[0]).orElseThrow().stat("damage-percent");
            ItemStack plain = fixture.storage[0].clone();
            check.accept(forge.corrupt(fixture.player, plain, 0, net.nerdsmp.paper.forge.arcane.ArcaneCorruption.BLOODTHIRST)
                == ArcaneForge.Outcome.APPLIED && fixture.storage[6].getAmount() == 64 - forge.settings().corruption().dust()
                && items.gear(fixture.storage[0]).orElseThrow().stat("damage-percent") == before + 10.0D
                && plain(fixture.storage[0]).contains("CORRUPTION"), "corruption applies exactly its listed bonus for its listed price");
            ItemStack corrupted = fixture.storage[0].clone();
            int dustNow = fixture.storage[6].getAmount();
            check.accept(core.enchanting().view(fixture.player).state() == net.nerdsmp.paper.forge.ArcaneEnchantingService.ViewState.CORRUPTED,
                "random table offers cannot bypass corruption");
            ItemStack tome = core.forge().createTome(ArcaneEnchantment.KEEN_EDGE, 1);
            int tomeCount = tome.getAmount();
            check.accept(core.forge().applyUpgrade(fixture.player, fixture.storage[0], tome)
                == net.nerdsmp.paper.forge.ArcaneForgeService.ApplyResult.CORRUPTED && tome.getAmount() == tomeCount,
                "tomes cannot bypass corruption and are not consumed on refusal");
            check.accept(forge.imbue(fixture.player, corrupted, 0, forge.registry().of(ArcaneEnchantment.KEEN_EDGE), 1)
                == ArcaneForge.Outcome.CORRUPTED && fixture.storage[6].getAmount() == dustNow, "a corrupted item takes no imbuement and spends nothing");
            check.accept(forge.cleanse(fixture.player, corrupted, 0) == ArcaneForge.Outcome.MISSING_DUST
                && items.arcaneCorruption(fixture.storage[0]).isPresent(), "cleansing without enough Dust changes nothing");
            fixture.storage[6] = core.enchanting().createArcaneDust(64);
            check.accept(forge.cleanse(fixture.player, corrupted, 0) == ArcaneForge.Outcome.APPLIED
                && items.arcaneCorruptionId(fixture.storage[0]).isEmpty()
                && items.gear(fixture.storage[0]).orElseThrow().stat("damage-percent") == before
                && fixture.storage[6].getAmount() == 64 - forge.cleanseCost(), "cleansing removes exactly the corruption for its price");

            // Attunement: raises only matching enchantments, refuses unrelated Aspects, clears for free.
            fixture.storage[0] = items.createCrafted(Material.IRON_SWORD);
            items.enchant(fixture.storage[0], ArcaneEnchantment.KEEN_EDGE, 3);
            fixture.storage[6] = core.enchanting().createArcaneDust(64);
            double keen = items.gear(fixture.storage[0]).orElseThrow().stat("damage-percent");
            ItemStack unattuned = fixture.storage[0].clone();
            check.accept(forge.attune(fixture.player, unattuned, 0, net.nerdsmp.paper.forge.arcane.ArcaneAspect.AEGIS)
                == ArcaneForge.Outcome.INCOMPATIBLE && fixture.storage[6].getAmount() == 64, "attuning to an unrelated Aspect is refused for free");
            check.accept(forge.attune(fixture.player, unattuned, 0, net.nerdsmp.paper.forge.arcane.ArcaneAspect.MIGHT)
                == ArcaneForge.Outcome.APPLIED && fixture.storage[6].getAmount() == 64 - forge.settings().attunement().dust()
                && Math.abs(items.gear(fixture.storage[0]).orElseThrow().stat("damage-percent") - (keen + 15.0D * forge.settings().attunement().bonusPercent() / 100.0D)) < 1e-9,
                "attunement raises only the attuned enchantment's own bonus");
            check.accept(forge.clearAttunement(fixture.player, fixture.storage[0].clone(), 0) == ArcaneForge.Outcome.APPLIED
                && items.gear(fixture.storage[0]).orElseThrow().stat("damage-percent") == keen
                && fixture.storage[6].getAmount() == 64 - forge.settings().attunement().dust(), "clearing an attunement is free and exact");
            check.accept(net.nerdsmp.api.model.SkillTreeCatalog.tree(net.nerdsmp.api.model.Skill.ENCHANTING).branches().size() == 4,
                "the Enchanting skill and its talent tree are loaded");

            var report = forge.dryRun(fixture.player);
            check.accept(!report.isEmpty() && report.getFirst().contains("nothing was changed"), "migration dry run is read-only and reports");
            core.playerData().update(account, yaml -> yaml.set("arcane.schema", 99));
            String malformed = core.playerData().read(account, yaml -> yaml.saveToString());
            ItemStack[] heldBefore = Arrays.stream(fixture.storage).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
            check.accept(forge.profile(fixture.player).isEmpty(), "future-schema knowledge fails closed rather than becoming an empty profile");
            check.accept(forge.research(fixture.player, List.of(new ArcaneForge.GemChoice(GemType.RUBY, 1)), false).outcome() == ArcaneForge.Outcome.FAILED
                && Arrays.equals(heldBefore, fixture.storage) && malformed.equals(core.playerData().read(account, yaml -> yaml.saveToString())),
                "unreadable knowledge preserves disk data and all inventory contents");
        } finally {
            core.sessions().quit(fixture.id);
            world.getBlockAt(fixture.at.getBlockX() + 2, fixture.at.getBlockY(), fixture.at.getBlockZ()).setType(Material.AIR, false);
        }
    }

    private static String plain(ItemStack item) {
        var lore = item.getItemMeta().lore();
        return lore == null ? "" : lore.stream()
            .map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize)
            .reduce("", (left, right) -> left + " | " + right);
    }

    private static final class Fixture {
        final UUID id = UUID.randomUUID();
        final ItemStack[] storage = new ItemStack[36];
        final ItemStack[] armor = new ItemStack[4];
        final ItemStack[] ender = new ItemStack[27];
        final Location at;
        final Player player;

        Fixture(World world) {
            at = new Location(world, 8.5, 121, 8.5);
            PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
                new Class<?>[]{PlayerInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getItemInMainHand" -> storage[0] == null ? new ItemStack(Material.AIR) : storage[0];
                    case "getItemInOffHand" -> new ItemStack(Material.AIR);
                    case "getStorageContents" -> storage.clone();
                    case "getContents" -> {
                        ItemStack[] all = Arrays.copyOf(storage, 40);
                        System.arraycopy(armor, 0, all, 36, 4);
                        yield all;
                    }
                    case "setStorageContents" -> { System.arraycopy((ItemStack[]) args[0], 0, storage, 0, 36); yield null; }
                    case "getArmorContents" -> armor.clone();
                    case "getItem" -> args[0] instanceof Integer slot && slot < 36 ? storage[slot] : null;
                    case "setItem" -> { storage[(Integer) args[0]] = (ItemStack) args[1]; yield null; }
                    case "getHeldItemSlot" -> 0;
                    case "getSize" -> 36;
                    case "getMaxStackSize" -> 64;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "ArcaneFixtureInventory";
                    default -> throw new UnsupportedOperationException("Arcane fixture inventory: " + method.getName());
                });
            Inventory enderChest = (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(), new Class<?>[]{Inventory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getContents", "getStorageContents" -> ender.clone();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "ArcaneFixtureEnderChest";
                    default -> throw new UnsupportedOperationException("Arcane fixture ender chest: " + method.getName());
                });
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> "ArcaneFixture";
                    case "getWorld" -> world;
                    case "getLocation" -> at.clone();
                    case "getInventory" -> inventory;
                    case "getEnderChest" -> enderChest;
                    case "getGameMode" -> GameMode.SURVIVAL;
                    case "isOnline", "hasPermission" -> true;
                    case "isDead" -> false;
                    case "sendMessage", "sendRichMessage", "sendActionBar", "playSound" -> null;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "ArcaneFixturePlayer";
                    default -> throw new UnsupportedOperationException("Arcane fixture: " + method.getName());
                });
        }
    }
}
