package dev.ohneb.afkcinematics.core;

public interface Geometry {
    boolean clear(Vec from, Vec to);
    boolean visible(Vec camera, Vec subject);

    default boolean valid(Shot shot, Vec entry) {
        Vec previous = entry;
        for (int step = 0; step <= 16; step++) {
            Vec current = shot.sample(shot.duration() * step / 16.0).position();
            if (!clear(previous, current) || !visible(current, shot.subject())) return false;
            previous = current;
        }
        return true;
    }
}
