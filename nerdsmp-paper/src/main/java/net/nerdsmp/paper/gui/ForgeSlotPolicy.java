package net.nerdsmp.paper.gui;

final class ForgeSlotPolicy {
    static final int PRIMARY_SLOT = 11;
    static final int CATALYST_SLOT = 15;
    static final int NO_SLOT = -1;

    private ForgeSlotPolicy() {
    }

    static int shiftDestination(InputKind kind, boolean primaryEmpty, boolean catalystEmpty) {
        return switch (kind) {
            case GEAR -> primaryEmpty ? PRIMARY_SLOT : NO_SLOT;
            case TOME -> catalystEmpty ? CATALYST_SLOT : NO_SLOT;
            case DUST, QUALITY_MATERIAL -> catalystEmpty ? CATALYST_SLOT : NO_SLOT;
            case GEM -> primaryEmpty ? PRIMARY_SLOT : catalystEmpty ? CATALYST_SLOT : NO_SLOT;
            case INVALID -> NO_SLOT;
        };
    }

    enum InputKind {
        GEAR,
        GEM,
        TOME,
        DUST,
        QUALITY_MATERIAL,
        INVALID
    }
}
