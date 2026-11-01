package dev.ohneb.afkcinematics.core;

import java.util.EnumSet;

public final class CinematicConfig {
    public enum Soundtrack { GAME, CREATIVE, DISCS, CUSTOM }
    public enum Motion { GENTLE, REDUCED, STILL }
    public enum Subject { MIXED, PLAYER, SCENERY }
    public enum Pace {
        QUICK(6, 10), SHORT(12, 18), RELAXED(20, 30), LONG(32, 46);
        public final double minimumSeconds, maximumSeconds;
        Pace(double minimumSeconds, double maximumSeconds) {
            this.minimumSeconds = minimumSeconds;
            this.maximumSeconds = maximumSeconds;
        }
    }

    public boolean enabled = true;
    public boolean automatic = true;
    public boolean autoFishing = true;
    public boolean frontTravel = true;
    public int idleSeconds = 30;
    public Motion motion = Motion.GENTLE;
    public Subject subject = Subject.MIXED;
    public int playerShare = 25;
    public Pace pace = Pace.RELAXED;
    public EnumSet<Shot.Type> shots = EnumSet.allOf(Shot.Type.class);
    public boolean useGameFov = true;
    public int customFov = 90;
    public int cinematicFps = 60;
    public boolean hideHud = true;
    public boolean hidePlayerNames = true;
    public boolean letterbox = false;
    public boolean music = false;
    public Soundtrack soundtrack = Soundtrack.GAME;
    public int musicVolume = 100;
    public String customMusic = "minecraft:music.creative";
    public boolean unfocused = false;
    public boolean keepChat = true;
    public boolean wildlife = true;

    public double fieldOfView(double normal) {
        return useGameFov ? normal : Math.clamp(customFov, 30, 140);
    }

    public double cameraRadius(double aspect) {
        if (useGameFov) return .25;
        // Enclose the .05-block near-plane corners at any camera rotation.
        double halfHeight = .05 * Math.tan(Math.toRadians(fieldOfView(70)) / 2);
        return Math.max(.25, Math.sqrt(.05 * .05 + halfHeight * halfHeight * (1 + aspect * aspect)) + .001);
    }
}
