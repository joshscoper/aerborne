package net.nerdsmp.paper.item;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RecipePatternTest {
    private boolean matches(List<String> shape, String... grid) {
        return RecipePattern.matches(shape, Arrays.asList(grid), String::isBlank, (symbol, actual) -> actual.equals(symbol.toString()));
    }
    @Test void acceptsShiftedAndMirroredPatternsInBothGridSizes() {
        assertTrue(matches(List.of("AB", " C"), "", "", "", "", "A", "B", "", "", "C"));
        assertTrue(matches(List.of("AB", " C"), "B", "A", "", "C", "", "", "", "", ""));
        assertTrue(matches(List.of("AB", " C"), "A", "B", "", "C"));
        assertTrue(matches(List.of("   ", " A ", "   "), "", "", "", "A"));
    }
    @Test void rejectsExtraIngredientsWrongIdentitiesAndOversizedShapes() {
        assertFalse(matches(List.of("AB", " C"), "A", "B", "unexpected", "C"));
        assertFalse(matches(List.of("AB", " C"), "A", "ordinary B base", "", "C"));
        assertFalse(matches(List.of("AAA"), "A", "A", "", ""));
        assertFalse(matches(List.of("A"), "A", ""));
        assertFalse(matches(List.of("  ", "  "), "", "", "", ""));
    }
}
