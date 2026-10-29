package dev.ohneb.afkcinematics.core;

import java.util.Random;

public final class CinematicSession {
    private final Geometry geometry;
    private final Vec subject;
    private final double bearing;
    private final CinematicConfig config;
    private final Random random;
    private ShotSearch search;
    private Shot shot;
    private Pose current;
    private final Pose entry;
    private long started;
    private long lastFrame;
    private boolean entering = true;
    private boolean ended;
    private double entrySeconds = 1.5;

    public CinematicSession(Vec subject, Pose entry, double bearing, CinematicConfig config, Geometry geometry, Random random) {
        this.subject = subject;
        this.entry = entry;
        this.current = entry;
        this.bearing = bearing;
        this.config = config;
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
            if (entering) {
                double distance = entry.position().distance(shot.start());
                double turn = Math.abs(Math.IEEEremainder(shot.sample(0).yaw() - entry.yaw(), 360));
                entrySeconds = Math.clamp(Math.max(distance / 1.5, turn / 45), 1.5, 4);
            }
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
        double sampledSeconds = entering ? 0 : elapsed;
        Pose next;
        if (entering) {
            next = entry.blend(shot.sample(0), elapsed / entrySeconds);
            if (elapsed >= entrySeconds) { entering = false; started += Math.round(entrySeconds * 1e9); }
        } else next = shot.sample(elapsed);
        Vec focus = next.lookAhead(Math.max(1, next.position().distance(shot.focus(sampledSeconds))));
        if (!geometry.clear(current.position(), next.position()) || !geometry.visible(next.position(), focus)) {
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
        return new ShotSearch(subject, current, bearing, config, last, first, random);
    }
}
