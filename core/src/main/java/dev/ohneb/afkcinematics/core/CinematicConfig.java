package dev.ohneb.afkcinematics.core;

import java.util.EnumSet;

public final class CinematicConfig {
    public enum StartMode { OFF, MANUAL, AUTOMATIC }
    public enum Soundtrack { GAME, CREATIVE, DISCS, CUSTOM }
    public enum Motion { GENTLE, REDUCED, STILL }
    public enum Subject { MIXED, PLAYER, SCENERY }
    public enum Lens {
        CINEMATIC(62), WIDE(78), GAME(0);
        private final double degrees;
        Lens(double degrees) { this.degrees = degrees; }
        public double fieldOfView(double normal, double aspect) {
            if (this == GAME) return normal;
            if (aspect >= 1) return degrees;
            // Keep horizontal framing when the window is taller than it is wide.
            return Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(degrees) / 2) / Math.min(1, Math.max(0.01, aspect))));
        }
        public double blend(double normal, double aspect, double strength) {
            return normal + (fieldOfView(normal, aspect) - normal) * Math.clamp(strength, 0, 1);
        }
    }
    public enum Pace {
        SHORT(12, 18), RELAXED(20, 30), LONG(32, 46);
        public final double minimumSeconds, maximumSeconds;
        Pace(double minimumSeconds, double maximumSeconds) {
            this.minimumSeconds = minimumSeconds;
            this.maximumSeconds = maximumSeconds;
        }
    }

    public boolean enabled = true;
    public boolean automatic = true;
    public int idleSeconds = 30;
    public Motion motion = Motion.GENTLE;
    public Subject subject = Subject.MIXED;
    public int playerShare = 25;
    public Pace pace = Pace.RELAXED;
    public EnumSet<Shot.Type> shots = EnumSet.allOf(Shot.Type.class);
    public Lens lens = Lens.CINEMATIC;
    public int cinematicFps = 60;
    public boolean hideHud = true;
    public boolean hidePlayerNames = true;
    public boolean letterbox = false;
    public boolean music = false;
    public Soundtrack soundtrack = Soundtrack.GAME;
    public int musicVolume = 100;
    public String customMusic = "minecraft:music.creative";
    public boolean unfocused = false;
    public StartMode boats = StartMode.AUTOMATIC;
    public StartMode minecarts = StartMode.AUTOMATIC;
    public StartMode flight = StartMode.AUTOMATIC;
    public boolean travelControls = true;
    public boolean keepChat = true;
    public boolean manualLook = false;
    public StartMode fishing = StartMode.AUTOMATIC;

    public boolean automatic(Scene.Kind activity) {
        return switch (activity) {
            case BOAT -> boats == StartMode.AUTOMATIC;
            case MINECART -> minecarts == StartMode.AUTOMATIC;
            case FLIGHT -> flight == StartMode.AUTOMATIC;
            case FISHING -> fishing == StartMode.AUTOMATIC;
            default -> automatic;
        };
    }
}
