package net.nerdsmp.paper.studio.qa;

import net.nerdsmp.paper.loot.profile.LootProfileDefinition;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static net.nerdsmp.paper.studio.qa.ContentRef.Type.*;
import static net.nerdsmp.paper.studio.qa.ContentValidator.*;
import static org.junit.jupiter.api.Assertions.*;

class ContentValidatorTest {
    private Catalog catalog(Set<ContentRef> known,List<Dependency> edges) { return new Catalog(known,edges,List.of(),List.of(),List.of(),List.of(),List.of()); }
    @Test void scansTypedRealDependenciesAndDoesNotConfuseEqualIDsAcrossContentTypes() {
        var blade=new ContentRef(ITEM,"blade"); var recipe=new ContentRef(RECIPE,"recipe");
        var report=validate(catalog(Set.of(blade,recipe),List.of(new Dependency(recipe,blade,"Recipe result"),
            new Dependency(blade,new ContentRef(SET,"blade"),"Set membership"))));
        assertEquals(1,report.count(Severity.ERROR)); assertFalse(report.referencesValid()); assertFalse(report.releaseApproved());
        assertTrue(report.findings().getFirst().message().contains("SET:blade")); assertEquals(1,report.counts().get(ITEM));
    }
    @Test void healthyContentReferencesDoNotAutomaticallyApproveARelease() {
        var report=validate(catalog(Set.of(new ContentRef(ITEM,"blade")),List.of()));
        assertTrue(report.referencesValid()); assertTrue(report.findings().isEmpty()); assertFalse(report.releaseApproved());
    }
    @Test void flagsExtremeStatXpAndEconomyBudgetsWithoutChangingCatalogs() {
        var table=new LootProfileDefinition("rewards",LootProfileDefinition.Mode.ADDITIVE,List.of(
            new LootProfileDefinition.Entry("coins",LootProfileDefinition.Kind.MARKS,"marks",1,100,100,false,0),
            new LootProfileDefinition.Entry("gear",LootProfileDefinition.Kind.ITEM,"blade",1,20,20,false,0)));
        var catalog=new Catalog(Set.of(new ContentRef(ITEM,"blade"),new ContentRef(MOB,"rat"),new ContentRef(LOOT,"rewards")),List.of(),
            List.of(new Item("blade",null,1,Map.of("damage",100D,"defense",100D,"skill-xp-percent",75D))),
            List.of(new Mob("rat",1,5,40,100,50,5,"")),List.of(table),List.of(),List.of());
        var report=validate(catalog); var codes=report.findings().stream().map(Finding::code).toList();
        for(String code:List.of("item_damage_budget","item_defense_budget","large_percent_stat","item_acquisition_unverified",
            "mob_xp_budget","mob_damage_budget","mob_ability_burst","default_mob_loot","marks_budget","loot_volume","additive_loot"))
            assertTrue(codes.contains(code),code);
        assertEquals(0,report.count(Severity.ERROR)); assertTrue(report.referencesValid()); assertEquals(table,catalog.loot().getFirst());
    }
    @Test void warnsAboutUnfinishedOrDenseZonesAndEmptyReplacementTables() {
        var report=validate(new Catalog(Set.of(),List.of(),List.of(),List.of(),
            List.of(new LootProfileDefinition("empty",LootProfileDefinition.Mode.REPLACE,List.of())),
            List.of(new Zone("new",false,false,0,0,64,5)),List.of()));
        var codes=report.findings().stream().map(Finding::code).toList();
        assertTrue(codes.containsAll(List.of("empty_loot","incomplete_zone","disabled_zone","dense_zone"))); assertEquals(0,report.count(Severity.ERROR));
    }
    @Test void externallyObservedLoadedWorldSafetyIssuesAndManualAcceptanceRemainVisible() {
        var ref=new ContentRef(ZONE,"crypt"); var observation=new Finding(Severity.WARNING,"unloaded",ref,"World unavailable; no loads attempted.");
        var report=validate(new Catalog(Set.of(ref),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(observation,
            new Finding(Severity.MANUAL,"restore",null,"Restore drill requires evidence."))));
        assertTrue(report.findings().contains(observation)); assertEquals(1,report.count(Severity.MANUAL)); assertFalse(report.releaseApproved());
    }
    @Test void reportIsBoundedAndSortedWithOmissionCountInsteadOfUnboundedGuiSpam() {
        var observations=new ArrayList<Finding>(); for(int i=0;i<600;i++) observations.add(new Finding(Severity.WARNING,"risk_"+i,null,"Check "+i));
        observations.add(new Finding(Severity.ERROR,"broken",new ContentRef(MOB,"boss"),"Missing table"));
        var report=validate(new Catalog(Set.of(),List.of(),List.of(),List.of(),List.of(),List.of(),observations));
        assertEquals(512,report.findings().size()); assertEquals(89,report.omitted()); assertEquals(Severity.ERROR,report.findings().getFirst().severity());
        assertThrows(UnsupportedOperationException.class,()->report.findings().clear()); assertThrows(UnsupportedOperationException.class,()->report.counts().clear());
    }
}
