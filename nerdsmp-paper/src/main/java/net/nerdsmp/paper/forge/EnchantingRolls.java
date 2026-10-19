package net.nerdsmp.paper.forge;

public final class EnchantingRolls {
    private EnchantingRolls() {
    }

    public static int level(int button, int bookshelfPower, int smithingLevel, double roll, int maximumLevel) {
        int power = Math.max(0, bookshelfPower);
        int skill = Math.max(0, smithingLevel);
        int cap = switch (Math.max(0, Math.min(2, button))) {
            case 0 -> power >= 8 && skill >= 10 ? 2 : 1;
            case 1 -> power >= 15 && skill >= 25 ? 4 : power >= 8 ? 3 : 2;
            default -> power >= 15 && skill >= 40 ? 5 : power >= 12 && skill >= 25 ? 4 : 3;
        };
        int floor = Math.max(1, cap - 1);
        int rolled = roll < 0.28D ? cap : floor;
        return Math.min(Math.max(1, maximumLevel), rolled);
    }

    public static int dustCost(int button) {
        return 4 * (Math.max(0, Math.min(2, button)) + 1);
    }

    public static int upgradeLevel(int currentLevel, int rolledLevel, int maximumLevel) {
        int current = Math.max(0, Math.min(maximumLevel, currentLevel));
        int rolled = Math.max(1, Math.min(maximumLevel, rolledLevel));
        return rolled > current ? rolled : 0;
    }
}
