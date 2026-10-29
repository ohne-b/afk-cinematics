package dev.ohneb.afkcinematics.core;

public interface Geometry {
    boolean clear(Vec from, Vec to);
    boolean visible(Vec camera, Vec subject);

    default Scene scenery(Vec anchor, Pose original, double bearing, int probe) { return null; }

    default boolean valid(Shot shot, Vec entry) {
        Vec previous = entry;
        for (int step = 0; step <= 16; step++) {
            double seconds = shot.duration() * step / 16.0;
            Vec current = shot.sample(seconds).position();
            if (!clear(previous, current) || !visible(current, shot.focus(seconds))) return false;
            previous = current;
        }
        return true;
    }
}
