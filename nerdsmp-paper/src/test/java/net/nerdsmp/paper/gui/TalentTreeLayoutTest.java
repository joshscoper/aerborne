package net.nerdsmp.paper.gui;

import net.nerdsmp.api.model.*;
import net.nerdsmp.api.service.ProgressionService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TalentTreeLayoutTest {
    @Test void everyProfessionAndAdventurerGraphFitsWithoutOverwritingActualNavigation() {
        List<Skill> trees=new ArrayList<>(Arrays.asList(Skill.values()));trees.add(null);
        for(Skill skill:trees) {
            Set<Integer> used=new HashSet<>(Set.of(0,8,45,47,MenuKit.backSlot(54),MenuKit.closeSlot(54),51,53));
            var cards=TalentTreePresentation.cards(ProgressionService.ProgressionSnapshot.empty(),skill);
            assertEquals(8,cards.size());
            for(var card:cards) {assertTrue(used.add(card.slot()),card.name());assertTrue(card.slot()<45);}
            for(var edge:TalentTreeLayout.edges()) for(int slot:edge.slots()) assertTrue(used.add(slot),"Connector collision "+slot);
        }
    }
    @Test void bothIslandPagesContainTwoCompleteDiamondsAndKeepFooterClear() {
        Set<Integer> used=new HashSet<>(Set.of(2,4,6,45,MenuKit.backSlot(54),MenuKit.closeSlot(54),51,53));
        for(int column=0;column<2;column++) {
            for(int i=0;i<4;i++) assertTrue(used.add(TalentTreeLayout.islandNode(column,i)));
            for(int slot:TalentTreeLayout.islandLinks(column)) assertTrue(used.add(slot));
        }
    }
}
