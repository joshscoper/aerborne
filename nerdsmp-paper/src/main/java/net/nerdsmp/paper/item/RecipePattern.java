package net.nerdsmp.paper.item;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Native shaped-recipe matching, including offsets, mirroring and empty cells outside the pattern. */
final class RecipePattern {
    private RecipePattern() { }

    static <T> boolean matches(List<String> shape, List<T> matrix, Predicate<T> empty,
                               BiPredicate<Character, T> ingredient) {
        int side = matrix.size() == 4 ? 2 : matrix.size() == 9 ? 3 : 0;
        if (side == 0 || shape.isEmpty()) return false;
        int top = 0, bottom = shape.size();
        while (top < bottom && shape.get(top).isBlank()) top++;
        while (bottom > top && shape.get(bottom - 1).isBlank()) bottom--;
        int left = 3, right = 0;
        for (int row = top; row < bottom; row++) for (int col = 0; col < shape.get(row).length(); col++) {
            if (shape.get(row).charAt(col) != ' ') { left = Math.min(left, col); right = Math.max(right, col + 1); }
        }
        int width = right - left, height = bottom - top;
        if (width <= 0 || width > side || height > side) return false;
        for (int y = 0; y <= side - height; y++) for (int x = 0; x <= side - width; x++) {
            for (boolean mirror : new boolean[]{false, true}) {
                boolean matches = true;
                for (int row = 0; row < side && matches; row++) for (int col = 0; col < side; col++) {
                    char symbol = row >= y && row < y + height && col >= x && col < x + width
                        ? shape.get(top + row - y).charAt(left + (mirror ? width - 1 - (col - x) : col - x)) : ' ';
                    T actual = matrix.get(row * side + col);
                    if (!(symbol == ' ' ? empty.test(actual) : !empty.test(actual) && ingredient.test(symbol, actual))) {
                        matches = false; break;
                    }
                }
                if (matches) return true;
            }
        }
        return false;
    }
}
