package dev.ohneb.afkcinematics.core;

public final class CinematicConfig {
    public enum Motion { GENTLE, REDUCED, STILL }
    public enum Distance {
        CLOSE(2.5), NORMAL(4.0), WIDE(5.5);
        public final double blocks;
        Distance(double blocks) { this.blocks = blocks; }
    }

    public boolean enabled = true;
    public boolean automatic = true;
    public int idleSeconds = 30;
    public Motion motion = Motion.GENTLE;
    public Distance distance = Distance.NORMAL;
    public boolean hideHud = true;
    public boolean letterbox = false;
    public boolean music = false;
    public boolean unfocused = false;
}
