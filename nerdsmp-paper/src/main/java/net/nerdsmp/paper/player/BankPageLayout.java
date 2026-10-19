package net.nerdsmp.paper.player;

/** Captures writable storage at the moment a bank page opens. */
record BankPageLayout(int page, int unlocked) {
    static final int PAGE_SIZE = 45;
    static final int CAPACITY = 54;

    BankPageLayout {
        if (page < 0 || page > 1 || unlocked < 0 || unlocked > CAPACITY) {
            throw new IllegalArgumentException("Invalid bank page");
        }
    }

    int index(int rawSlot) {
        if (rawSlot < 0 || rawSlot >= PAGE_SIZE) {
            return -1;
        }
        int index = page * PAGE_SIZE + rawSlot;
        return index < CAPACITY ? index : -1;
    }

    int writableIndex(int rawSlot) {
        int index = index(rawSlot);
        return index >= 0 && index < unlocked ? index : -1;
    }
}
