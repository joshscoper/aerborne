package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.*;
import net.nerdsmp.paper.engine.ItemStats;
import net.nerdsmp.paper.forge.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** A new editor/item stat must have a named runtime consumer, not only a tooltip. */
class BonusConsumerCoverageTest {
    @Test void everyEquipmentStatHasAnExplicitGameplayConsumer() throws Exception {
        Map<String,List<String>> consumers=Map.ofEntries(
            Map.entry("item/GearSetService.java",List.of("damage","projectile-damage","defense","damage-flat","damage-percent","projectile-damage-percent","execute-damage-percent","life-steal-percent")),
            Map.entry("listener/SkillPower.java",List.of("mining-power","fishing-power","gathering-power","excavation-power")),
            Map.entry("listener/ActivityBonuses.java",List.of("skill-xp-percent","job-marks-percent")),
            Map.entry("forge/ArcaneForgeService.java",List.of("geode-find-percent","durability-save-percent")),
            Map.entry("alchemy/AlchemyService.java",List.of("poison-potency-percent")),
            Map.entry("listener/SkillListener.java",List.of("harvest-extra-percent")),
            Map.entry("activity/FishingService.java",List.of("fishing-luck-percent","treasure-find-percent")),
            Map.entry("activity/ExcavationService.java",List.of("excavation-find-percent")),
            Map.entry("forge/arcane/ArcaneEffectService.java",List.of("bleed-percent","vein-size","chain-harvest")));
        Set<String> covered=new HashSet<>();
        for(var entry:consumers.entrySet()) {
            String source=Files.readString(Path.of("src/main/java/net/nerdsmp/paper").resolve(entry.getKey()));
            for(String stat:entry.getValue()) {assertTrue(source.contains("\""+stat+"\""),entry.getKey()+" lost "+stat);covered.add(stat);}
        }
        assertEquals(ItemStats.allowed(),covered);
        for(ItemAffix affix:ItemAffix.values()) assertTrue(covered.contains(affix.stat()),affix.name());
        for(GemType gem:GemType.values()) assertTrue(covered.containsAll(gem.stats(4).keySet()),gem.name());
        for(ArcaneEnchantment enchantment:ArcaneEnchantment.values()) assertTrue(covered.containsAll(enchantment.stats(1).keySet()),enchantment.name());
    }
    @Test void allTalentStatsAreClassifiedAsGlobalOrActivityScoped() {
        Set<String> consumed=Set.of("skill-xp-percent","job-marks-percent","geode-find-percent","extra-drop-percent","hunting-damage-percent",
            "durability-save-percent","reagent-save-percent","building-fall-reduction-percent","meal-duration-percent",
            "expedition-geode-percent","equipment-preservation-percent","husbandry-quality-percent","fishing-luck-percent",
            "life-steal-percent","excavation-find-percent","poison-potency-percent","meal-nutrition-percent","treasure-find-percent",
            "imbue-dust-discount-percent","research-progress-percent","signature-cooldown-reduction-percent");
        for(Skill skill:Skill.values()) for(var branch:SkillTreeCatalog.tree(skill).branches()) for(var node:branch.nodes())
            assertTrue(consumed.contains(node.stat()),node.id()+" has no classified bonus consumer");
    }
    @Test void lifeStealCannotHealOverkillAndKeepsItsFourHealthCap() {
        assertEquals(0.2,GearMath.lifeSteal(100,2,10),1E-9);
        assertEquals(4,GearMath.lifeSteal(100,100,25),1E-9);
        assertEquals(0,GearMath.lifeSteal(0,10,10));assertEquals(0,GearMath.lifeSteal(10,0,10));
    }
}
