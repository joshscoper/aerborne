package net.nerdsmp.smoke;

import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.item.PaperItemService;
import net.nerdsmp.paper.item.SignatureAffix;
import net.nerdsmp.paper.loot.ExpansionLootTables;
import net.nerdsmp.paper.market.MarketItems;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.nio.file.Files;
import java.util.*;
import java.util.function.BiConsumer;

/** Real Paper metadata, recipe registration, set activation and deterministic loot verification. */
public final class ItemExpansionSmoke {
    private ItemExpansionSmoke() { }
    public static void run(NerdSmpPlugin core, BiConsumer<Boolean,String> check) throws Exception {
        var items=core.items();var entries=items.expansionEntries();
        check.accept(entries.size()==100,"expansion installs exactly 100 additional item IDs");
        check.accept(Arrays.stream(org.bukkit.event.entity.EntityDeathEvent.getHandlerList().getRegisteredListeners())
            .anyMatch(listener->listener.getPlugin()==core&&listener.getListener()==core.expansionLoot()),
            "expansion kill loot listener is wired into Paper");
        check.accept(Arrays.stream(org.bukkit.event.block.BlockDropItemEvent.getHandlerList().getRegisteredListeners())
            .anyMatch(listener->listener.getPlugin()==core&&listener.getListener()==core.autoSmelt()),
            "Furnace Heart native-drop listener is wired into Paper");
        var file=core.getDataFolder().toPath().resolve("content/skyforge-items.yml");
        byte[] before=Files.readAllBytes(file);var yaml=new YamlConfiguration();yaml.load(file.toFile());
        Map<String,EnumMap<EquipmentSlot,ItemStack>> sets=new HashMap<>();Set<String> signatures=new HashSet<>();
        Map<String,Long> rarities=new HashMap<>();
        for(var entry:entries.entrySet()) {
            ItemId id=entry.getKey();var metadata=entry.getValue();ItemStack item=items.create(id);
            var gear=items.gear(item).orElseThrow();rarities.merge(gear.rarity(),1L,Long::sum);
            check.accept(items.isAuthentic(item)&&gear.rarity().equals(metadata.rarity()),"authentic expansion rarity: "+id.value());
            check.accept(item.getItemMeta().getItemModel()==null&&items.socketCapacity(item)>0
                &&!item.getItemMeta().isHideTooltip(),"vanilla texture, sockets and RPG tooltip: "+id.value());
            check.accept(items.gear(MarketItems.decode(MarketItems.encode(item))).orElseThrow().equals(gear),
                "expansion stats survive Paper serialization: "+id.value());
            if(metadata.setId()!=null) {
                EquipmentSlot slot=switch(gear.slot()) {
                    case HEAD->EquipmentSlot.HEAD;case CHEST->EquipmentSlot.CHEST;case LEGS->EquipmentSlot.LEGS;
                    case FEET->EquipmentSlot.FEET;case MAIN_HAND->EquipmentSlot.HAND;case OFF_HAND->EquipmentSlot.OFF_HAND;
                    default->throw new AssertionError("Missing equipment slot");
                };
                sets.computeIfAbsent(metadata.setId(),ignored->new EnumMap<>(EquipmentSlot.class)).put(slot,item.clone());
            }
            var recipe=core.recipes().recipes().stream().filter(candidate->candidate.id().equals("skyforge_"+id.value())).findFirst();
            check.accept(recipe.isPresent()==metadata.acquisition().equals("craft"),"acquisition has exactly the intended recipe route: "+id.value());
            if(recipe.isPresent()) {
                var registered=Bukkit.getRecipe(new NamespacedKey(core,"crafted_"+recipe.get().id()));
                check.accept(registered!=null&&items.describe(registered.getResult()).orElseThrow().itemId().equals(id),
                    "real registered expansion recipe result: "+id.value());
            }
            if(gear.rarity().equals("MYTHIC")) {
                SignatureAffix signature=items.signatureAffix(item).orElseThrow();signatures.add(signature.id());
                var stats=yaml.getConfigurationSection("items."+id.value()+".stats");
                signature.stats().forEach((stat,value)->check.accept(Math.abs(gear.stats().getOrDefault(stat,0.0)
                    -(stats.getDouble(stat)+value))<0.000001,"signature merges once into real gear stats: "+id.value()+" / "+stat));
                check.accept(items.improveQuality(item)==PaperItemService.QualityUpgradeResult.APPLIED
                    &&items.signatureAffix(item).orElseThrow()==signature,"workmanship retains signature: "+id.value());
                check.accept(items.socket(item,net.nerdsmp.paper.forge.GemType.RUBY,1)==PaperItemService.UpgradeResult.APPLIED
                    &&items.signatureAffix(item).orElseThrow()==signature,"socketing retains signature: "+id.value());
                check.accept(items.signatureAffix(MarketItems.decode(MarketItems.encode(item))).orElseThrow()==signature,
                    "upgraded signature survives market serialization: "+id.value());
                ItemStack sealed=items.createLoot(id,3,true,id.value().hashCode());var identity=items.lootInstance(sealed);
                check.accept(items.gear(sealed).isEmpty()&&items.signatureAffix(sealed).isEmpty()
                    &&items.sealedGear(sealed).isPresent(),"sealed Mythic has no active signature or gear stats: "+id.value());
                var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
                check.accept(sealed.getItemMeta().lore().stream().noneMatch(line->plain.serialize(line).contains(signature.displayName())),
                    "sealed tooltip conceals signature: "+id.value());
                check.accept(items.identify(sealed)&&items.signatureAffix(sealed).orElseThrow()==signature
                    &&items.lootInstance(sealed).equals(identity)&&items.affixes(sealed).size()==3,
                    "identification reveals signature without rerolling loot identity: "+id.value());
                ItemStack forged=sealed.clone();var meta=forged.getItemMeta();
                meta.getPersistentDataContainer().set(new NamespacedKey(core,"signature_affix"),PersistentDataType.STRING,"unknown_signature");forged.setItemMeta(meta);
                check.accept(items.signatureAffix(forged).isEmpty()&&items.gear(forged).isEmpty(),"forged signature tag is rejected: "+id.value());
                ItemStack stacked=sealed.clone();stacked.setAmount(2);
                check.accept(items.signatureAffix(stacked).isEmpty()&&items.gear(stacked).isEmpty(),"stacked signature gear cannot activate: "+id.value());
            } else check.accept(items.signatureAffix(item).isEmpty(),"ordinary equipment cannot activate a signature: "+id.value());
        }
        check.accept(rarities.equals(Map.of("UNCOMMON",20L,"RARE",25L,"EPIC",25L,"LEGENDARY",20L,"MYTHIC",10L)),"all five promised rarity counts load at runtime");
        check.accept(signatures.size()==10&&sets.size()==8,"ten distinct Mythic signatures and eight new sets load");
        for(var entry:sets.entrySet()) {
            var equipped=entry.getValue();var definition=items.setDefinition(entry.getKey()).orElseThrow();
            check.accept(equipped.size()==6,"six distinct usable set slots: "+entry.getKey());
            var profile=core.gearSets().profile(equipped);
            check.accept(profile.setPieces().get(entry.getKey())==6&&profile.activeBonuses().size()==3,"all three actual set thresholds activate: "+entry.getKey());
            var expected=new HashMap<String,Double>();equipped.values().forEach(item->items.gear(item).orElseThrow().stats().forEach((stat,value)->expected.merge(stat,value,Double::sum)));
            definition.bonuses().values().forEach(bonus->bonus.stats().forEach((stat,value)->expected.merge(stat,value,Double::sum)));
            check.accept(profile.stats().equals(expected),"set and equipment totals are applied exactly once: "+entry.getKey());
        }
        for(var source:ExpansionLootTables.Source.values()) for(double rarity:new double[]{0,0.5,0.9,Math.nextDown(1.0)}) {
            var drop=core.expansionLoot().roll(source,0,rarity,0.4,0,9901).orElseThrow();
            var id=items.describe(drop).orElseThrow().itemId();
            check.accept(entries.get(id).acquisition().equals("drop")&&items.isUnidentified(drop)&&items.sealedGear(drop).isPresent(),
                "live loot creates only drop-only sealed gear: "+source+" / "+rarity);
        }
        var mythic=core.expansionLoot().roll(ExpansionLootTables.Source.BOSS,0,Math.nextDown(1.0),0,0.99,42).orElseThrow();
        check.accept(items.gear(mythic).orElseThrow().rarity().equals("MYTHIC")&&items.signatureAffix(mythic).isPresent()
            &&items.affixes(mythic).size()==3,"forced boss chase roll yields a real identified three-affix signature Mythic");
        check.accept(core.expansionLoot().roll(ExpansionLootTables.Source.BOSS,0.5,0.99,0,0,0).isEmpty(),"boss loot chance boundary grants nothing");
        new PaperItemService(core);
        check.accept(Arrays.equals(before,Files.readAllBytes(file)),"loading the additive catalog again preserves the operator file byte-for-byte");
        check.accept(core.items().itemIds().contains(new ItemId("astral_covenant_blade")),"existing equipment catalog remains available");
        checkVanillaTrim(core,check);
        core.getLogger().info("ZONE_SMOKE_ITEM_EXPANSION_COMPLETE PASS");
    }

    private static void checkVanillaTrim(NerdSmpPlugin core,BiConsumer<Boolean,String> check) throws Exception {
        var service=core.rewardDrops();var field=service.getClass().getDeclaredField("adjustments");field.setAccessible(true);
        Object original=field.get(service);
        Class<?> adjustment=Class.forName("net.nerdsmp.paper.loot.RewardDropService$DropAdjustment");
        var constructor=adjustment.getDeclaredConstructor(double.class,int.class);constructor.setAccessible(true);
        var method=service.getClass().getDeclaredMethod("applyVanillaAdjustments",org.bukkit.entity.EntityType.class,List.class);method.setAccessible(true);
        try {
            field.set(service,Map.of(org.bukkit.entity.EntityType.ZOMBIE,Map.of(org.bukkit.Material.NETHERITE_PICKAXE,constructor.newInstance(0.0,0))));
            ItemStack named=core.items().create(new ItemId("sunforge_pickaxe"));
            List<ItemStack> drops=new ArrayList<>(List.of(named,new ItemStack(org.bukkit.Material.NETHERITE_PICKAXE)));
            method.invoke(service,org.bukkit.entity.EntityType.ZOMBIE,drops);
            check.accept(drops.size()==1&&drops.getFirst()==named&&core.items().signatureAffix(named).orElseThrow()==SignatureAffix.FURNACE_HEART,
                "vanilla material trimming removes plain gear but never an authenticated Mythic reward");
        } finally { field.set(service,original); }
    }
}
