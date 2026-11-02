package dev.ohneb.afkcinematics.core;

/** A subject and a starting viewpoint, both selected from loaded terrain. */
public record Scene(Vec focus, Vec vantage, Kind kind, double interest, Animal animal) {
    public record Animal(int id, java.util.UUID uuid, String species) { }
    public Scene(Vec focus, Vec vantage, Kind kind, double interest) { this(focus, vantage, kind, interest, null); }
    public enum Kind {
        PLAYER, LANDSCAPE, AERIAL, GROUND, WATER, WILDLIFE, BOAT, MINECART, FLIGHT, FOLLOW, SWIM, RIDE, FISHING, SLEEP;
        public boolean travel() { return this == BOAT || this == MINECART || this == FLIGHT || this == FOLLOW || this == SWIM || this == RIDE; }
    }
    public static final int PROBES = 64;
    public Scene {
        if (focus == null || vantage == null || kind == null || !Double.isFinite(interest))
            throw new IllegalArgumentException("Invalid scene");
    }

    public boolean scenery() { return kind != Kind.PLAYER && !kind.travel() && kind != Kind.FISHING && kind != Kind.SLEEP; }

    public boolean near(Vec anchor) {
        Vec target = focus.subtract(anchor), view = vantage.subtract(anchor);
        return Math.hypot(target.x(), target.z()) <= 18 && Math.abs(target.y()) <= 52
            && Math.hypot(view.x(), view.z()) <= 30 && Math.abs(view.y()) <= 64;
    }

    public static boolean inRange(Vec point, Vec anchor) {
        Vec offset = point.subtract(anchor);
        return Math.hypot(offset.x(), offset.z()) <= 32 && Math.abs(offset.y()) <= 64;
    }
}
