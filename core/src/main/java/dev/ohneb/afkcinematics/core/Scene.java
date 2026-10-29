package dev.ohneb.afkcinematics.core;

/** A subject and a starting viewpoint, both selected from loaded terrain. */
public record Scene(Vec focus, Vec vantage, boolean scenery, double interest) {
    public static final int PROBES = 40;
    public Scene {
        if (focus == null || vantage == null || !Double.isFinite(interest))
            throw new IllegalArgumentException("Invalid scene");
    }

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
