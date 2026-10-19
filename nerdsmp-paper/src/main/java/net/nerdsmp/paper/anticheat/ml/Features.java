package net.nerdsmp.paper.anticheat.ml;

import java.util.List;

/** The combat-window feature vector. Order is part of the saved model format: append only, never reorder. */
public final class Features {
    public static final int CPS = 0;
    public static final int CLICK_MODE = 1;
    public static final int CLICK_STD = 2;
    public static final int CLICK_ENTROPY = 3;
    public static final int YAW_MEAN = 4;
    public static final int YAW_STD = 5;
    public static final int PITCH_MEAN = 6;
    public static final int PITCH_STD = 7;
    public static final int YAW_JERK = 8;
    public static final int PITCH_LOCK = 9;
    public static final int GCD_ERROR = 10;
    public static final int SNAP_RATIO = 11;
    public static final int AIM_OFFSET_MEAN = 12;
    public static final int AIM_OFFSET_STD = 13;
    public static final int REACH_MEAN = 14;
    public static final int REACH_MAX = 15;
    public static final int HITBOX_MISS = 16;
    public static final int MISSING_SWING = 17;
    public static final int ATTACK_RATE = 18;
    public static final int TARGET_SWITCH = 19;
    public static final int PING = 20;
    public static final int RULE_FLAGS = 21;
    public static final int COUNT = 22;

    public static final List<String> NAMES = List.of(
        "Click speed", "Click rhythm repetition", "Click interval spread", "Click rhythm variety",
        "Average turn speed", "Turn speed spread", "Average vertical aim", "Vertical aim spread",
        "Turn jerkiness", "Vertical aim locked", "Mouse-step mismatch", "Snap-to-target turns",
        "Aim offset from target centre", "Aim offset spread", "Average reach", "Longest reach",
        "Hits outside the hitbox", "Attacks without arm swing", "Attacks per second", "Target switching",
        "Ping", "Rule-based flags");

    private Features() {
    }
}
