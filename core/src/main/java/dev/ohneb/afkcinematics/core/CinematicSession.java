package dev.ohneb.afkcinematics.core;

import java.util.Random;

public final class CinematicSession {
    private final Geometry geometry;
    private final Vec subject;
    private final double bearing;
    private final double distance;
    private final CinematicConfig.Motion motion;
    private final Random random;
    private ShotSearch search;
    private Shot shot;
    private Pose current;
    private final Pose entry;
    private long started;
    private long lastFrame;
    private boolean entering = true;
    private boolean ended;

    public CinematicSession(Vec subject, Pose entry, double bearing, CinematicConfig config, Geometry geometry, Random random) {
        this.subject = subject;
        this.entry = entry;
        this.current = entry;
        this.bearing = bearing;
        this.distance = config.distance.blocks;
        this.motion = config.motion;
        this.geometry = geometry;
        this.random = random;
        search = search(null, true);
    }

    public void tick(long now) {
        if (ended) return;
        if (shot != null && !entering && search == null && (now - started) / 1e9 >= shot.duration())
            search = search(shot.type(), false);
        if (search == null) return;
        for (int i = 0; i < 2 && !search.exhausted(); i++) {
            Shot candidate = search.next(geometry);
            if (candidate == null) continue;
            shot = candidate;
            search = null;
            started = now;
            lastFrame = now;
            return;
        }
        if (search.exhausted()) ended = true;
    }

    public Pose frame(long now) {
        if (ended || shot == null) return null;
        if (now < lastFrame || now - lastFrame > 1_000_000_000L) { ended = true; return null; }
        lastFrame = now;
        double elapsed = (now - started) / 1e9;
        Pose next;
        if (entering) {
            next = entry.blend(shot.sample(0), elapsed / 1.5);
            if (elapsed >= 1.5) { entering = false; started += 1_500_000_000L; }
        } else next = shot.sample(elapsed);
        if (!geometry.clear(current.position(), next.position()) || !geometry.visible(next.position(), subject)) {
            ended = true;
            return null;
        }
        current = next;
        return current;
    }

    public boolean ended() { return ended; }
    public boolean showing() { return shot != null && !ended; }
    public Pose current() { return current; }

    private ShotSearch search(Shot.Type last, boolean first) {
        return new ShotSearch(subject, current, bearing, distance, motion, last, first, random);
    }
}
