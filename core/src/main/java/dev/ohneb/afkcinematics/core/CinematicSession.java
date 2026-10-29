package dev.ohneb.afkcinematics.core;

import java.util.Random;

public final class CinematicSession {
    private enum Phase { ENTRY, SHOT, FADE_OUT, CUT, FADE_IN }
    private final Geometry geometry;
    private final Vec subject;
    private final double bearing;
    private final CinematicConfig config;
    private final Random random;
    private final Pose entry;
    private ShotPlanner planner;
    private ShotPlanner.Plan pending;
    private Shot shot;
    private Scene scene;
    private Pose current;
    private long started, lastFrame;
    private Phase phase = Phase.ENTRY;
    private boolean ended;
    private double entrySeconds = 1.5;
    private double fade;

    public CinematicSession(Vec subject, Pose entry, double bearing, CinematicConfig config, Geometry geometry, Random random) {
        this.subject = subject;
        this.entry = entry;
        this.current = entry;
        this.bearing = bearing;
        this.config = config;
        this.geometry = geometry;
        this.random = random;
        planner = planner();
    }

    public void tick(long now) {
        if (ended) return;
        if (shot != null && phase == Phase.SHOT && planner == null && (now - started) / 1e9 >= shot.duration())
            planner = planner();
        if (planner == null) return;
        for (int i = 0; i < 2 && !planner.exhausted(); i++) {
            var plan = planner.next(geometry);
            if (plan == null) continue;
            if (plan.cut()) {
                pending = plan;
                phase = Phase.FADE_OUT;
            } else {
                shot = plan.shot();
                scene = plan.scene();
                phase = plan.entering() ? Phase.ENTRY : Phase.SHOT;
                if (plan.entering()) {
                    double distance = entry.position().distance(shot.start());
                    double turn = Math.abs(Math.IEEEremainder(shot.sample(0).yaw() - entry.yaw(), 360));
                    entrySeconds = Math.clamp(Math.max(distance / 1.5, turn / 45), 1.5, 4);
                }
            }
            planner = null;
            started = now;
            lastFrame = now;
            return;
        }
        if (planner.exhausted()) ended = true;
    }

    public Pose frame(long now) {
        if (ended || shot == null && pending == null) return null;
        if (now < lastFrame || now - lastFrame > 1_000_000_000L) return end();
        lastFrame = now;
        double elapsed = (now - started) / 1e9;
        if (phase == Phase.FADE_OUT) {
            if (!geometry.clear(current.position(), current.position())) return end();
            fade = Pose.ease(elapsed / 0.7);
            // Render the old viewpoint fully covered before changing its position.
            if (fade == 1) phase = Phase.CUT;
            return current;
        }
        if (phase == Phase.CUT) {
            Pose next = pending.shot().sample(0);
            if (!valid(next, pending.shot().focus(0), next.position())) return end();
            shot = pending.shot();
            scene = pending.scene();
            pending = null;
            current = next;
            phase = Phase.FADE_IN;
            started = now;
            fade = 1;
            return current;
        }
        Pose next;
        double sampledSeconds = elapsed;
        if (phase == Phase.ENTRY) {
            sampledSeconds = 0;
            next = entry.blend(shot.sample(0), elapsed / entrySeconds);
            if (elapsed >= entrySeconds) { phase = Phase.SHOT; started += Math.round(entrySeconds * 1e9); }
        } else if (phase == Phase.FADE_IN) {
            sampledSeconds = 0;
            next = shot.sample(0);
            fade = 1 - Pose.ease(elapsed / 0.9);
            if (fade == 0) { phase = Phase.SHOT; started = now; }
        } else next = shot.sample(elapsed);
        Vec focus = next.lookAhead(Math.max(1, next.position().distance(shot.focus(sampledSeconds))));
        if (!valid(next, focus, current.position())) return end();
        current = next;
        return current;
    }

    private boolean valid(Pose pose, Vec focus, Vec previous) {
        return Scene.inRange(pose.position(), subject) && geometry.clear(previous, pose.position())
            && geometry.visible(pose.position(), focus);
    }

    private Pose end() { ended = true; fade = 0; return null; }
    public boolean ended() { return ended; }
    public boolean showing() { return (shot != null || pending != null) && !ended; }
    public Pose current() { return current; }
    public double fade() { return fade; }
    public boolean scenery() { return scene != null && scene.scenery(); }

    private ShotPlanner planner() {
        return new ShotPlanner(subject, current, entry, bearing, config, scene, shot == null ? null : shot.type(), random);
    }
}
