package dev.ohneb.afkcinematics.core;

import java.util.EnumSet;

public final class CinematicConfig {
    public enum Motion { GENTLE, REDUCED, STILL }
    public enum Pace {
        SHORT(12, 18), RELAXED(20, 30), LONG(32, 46);
        public final double minimumSeconds, maximumSeconds;
        Pace(double minimumSeconds, double maximumSeconds) {
            this.minimumSeconds = minimumSeconds;
            this.maximumSeconds = maximumSeconds;
        }
    }
    public enum Distance {
        CLOSE(2.5), NORMAL(4.0), WIDE(5.5);
        public final double blocks;
        Distance(double blocks) { this.blocks = blocks; }
    }

    public boolean enabled = true;
    public boolean automatic = true;
    public int idleSeconds = 30;
    public Motion motion = Motion.GENTLE;
    public Pace pace = Pace.RELAXED;
    public EnumSet<Shot.Type> shots = EnumSet.allOf(Shot.Type.class);
    public Distance distance = Distance.NORMAL;
    public boolean hideHud = true;
    public boolean letterbox = false;
    public boolean music = false;
    public boolean unfocused = false;
}
