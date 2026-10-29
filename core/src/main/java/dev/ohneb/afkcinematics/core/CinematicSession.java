package dev.ohneb.afkcinematics.core;

import java.util.Random;

public final class CinematicSession {
    private enum Phase { ENTRY, SHOT, FADE_OUT, CUT, FADE_IN }
    private final Geometry geometry;
    private final Vec subject;
    private double bearing;
    private final Scene.Kind activity;
    private final CinematicConfig config;
    private final Random random;
    private final Pose entry;
    private ShotPlanner planner;
    private ShotPlanner.Plan pending;
    private Shot shot;
    private Scene scene;
    private final ShotHistory history = new ShotHistory();
    private Pose current;
    private Vec currentFocus;
    private long started, handoff, clockOffset;
    private long lastUpdate = Long.MIN_VALUE;
    private long captureStarted;
    private int captures;
    private int take;
    private Vec fishingTarget;
    private Phase phase = Phase.ENTRY;
    private boolean ended;
    private boolean covered;
    private boolean advanceRequested;
    private boolean heldForNext;
    private double entrySeconds = 1.5;
    private double fade;
    private double lensStrength;

    public CinematicSession(Vec subject, Pose entry, double bearing, CinematicConfig config, Geometry geometry, Random random) {
        this(subject, entry, bearing, config, geometry, random, Scene.Kind.PLAYER);
    }

    public CinematicSession(Vec subject, Pose entry, double bearing, CinematicConfig config, Geometry geometry, Random random, Scene.Kind activity) {
        this(subject, entry, bearing, config, geometry, random, activity, null);
    }

    public CinematicSession(Vec subject, Pose entry, double bearing, CinematicConfig config, Geometry geometry, Random random, Scene.Kind activity, Vec bobber) {
        this.subject = subject;
        this.entry = entry;
        this.current = entry;
        this.bearing = bearing;
        this.config = config;
        this.geometry = geometry;
        this.activity = activity;
        this.fishingTarget = bobber;
        this.random = random;
        planner = planner(current);
    }

    public void tick(long now) {
        if (ended || captures > 0) return;
        now = timeline(now);
        if (ended) return;
        if (shot != null && phase == Phase.SHOT) {
            if (advanceRequested && !heldForNext) {
                planner = planner(current);
                pending = null;
                heldForNext = true;
                handoff = now;
                advanceRequested = false;
            } else if (planner == null && pending == null
                && (now - started) / 1e9 >= shot.duration() - (activity == Scene.Kind.PLAYER ? ShotPlanner.preparationSeconds(config) : 4)) {
                planner = planner(shot.sample(shot.duration()));
                handoff = started + Math.round(shot.duration() * 1e9);
            }
        }
        if (planner != null) {
            for (int i = 0; i < 2 && !planner.exhausted(); i++) {
                var plan = planner.next(geometry);
                if (plan == null) continue;
                pending = plan;
                planner = null;
                break;
            }
            if (planner != null && planner.exhausted()) {
                if (shot == null || heldForNext || now >= handoff) ended = true;
                return;
            }
        }
        if (pending == null || phase != Phase.ENTRY && phase != Phase.SHOT) return;
        if (shot != null && phase == Phase.SHOT && now < handoff) return;
        boolean initial = shot == null;
        if (!initial && phase == Phase.SHOT && !heldForNext) {
            Pose end = shot.sample(shot.duration());
            Vec focus = shot.focus(shot.duration());
            if (!valid(end, focus, current.position(), scene)) { end(); return; }
            current = end;
            currentFocus = focus;
        }
        var plan = pending;
        if (!geometry.forScene(plan.scene()).valid(plan.shot(), plan.cut() ? plan.shot().start() : current.position())) { end(); return; }
        if (plan.cut()) {
            phase = Phase.FADE_OUT;
            covered = false;
        } else {
            shot = plan.shot();
            setScene(plan.scene());
            pending = null;
            phase = plan.entering() ? Phase.ENTRY : Phase.SHOT;
            if (plan.entering()) {
                double distance = entry.position().distance(shot.start());
                Pose target = shot.sample(0);
                double turn = Math.max(Math.abs(Math.IEEEremainder(target.yaw() - entry.yaw(), 360)),
                    Math.abs(target.pitch() - entry.pitch()));
                entrySeconds = Math.clamp(Math.max(distance / 1.5, turn / 45), 1.5, 6);
            }
        }
        heldForNext = false;
        started = now;
    }

    public Pose frame(long now) {
        if (ended || shot == null && pending == null) return null;
        if (captures > 0) {
            boolean safe = currentFocus == null
                ? geometry.forScene(scene).clear(current.position(), current.position())
                : valid(current, currentFocus, current.position(), scene);
            return safe ? current : end();
        }
        now = timeline(now);
        if (ended) return null;
        if (heldForNext && phase == Phase.SHOT)
            return valid(current, currentFocus, current.position(), scene) ? current : end();
        double elapsed = (now - started) / 1e9;
        if (phase == Phase.FADE_OUT) {
            if (!geometry.forScene(scene).clear(current.position(), current.position())) return end();
            fade = Pose.ease(elapsed / 0.35);
            // Render the old viewpoint fully covered before changing its position.
            if (fade == 1) phase = Phase.CUT;
            return current;
        }
        if (phase == Phase.CUT) {
            if (!covered) return geometry.forScene(scene).clear(current.position(), current.position()) ? current : end();
            Pose next = pending.shot().sample(0);
            if (!valid(next, pending.shot().focus(0), next.position(), pending.scene())) return end();
            shot = pending.shot();
            setScene(pending.scene());
            pending = null;
            current = next;
            currentFocus = shot.focus(0);
            lensStrength = 1;
            phase = Phase.FADE_IN;
            started = now;
            fade = 1;
            covered = false;
            return current;
        }
        Pose next;
        double sampledSeconds = elapsed;
        if (phase == Phase.ENTRY) {
            sampledSeconds = 0;
            next = entry.blend(shot.sample(0), elapsed / entrySeconds);
            lensStrength = Pose.ease(elapsed / entrySeconds);
            if (elapsed >= entrySeconds) { phase = Phase.SHOT; started += Math.round(entrySeconds * 1e9); }
        } else if (phase == Phase.FADE_IN) {
            sampledSeconds = 0;
            next = shot.sample(0);
            if (!covered) started = now;
            elapsed = (now - started) / 1e9;
            fade = 1 - Pose.ease(elapsed / 0.45);
            if (fade == 0) { phase = Phase.SHOT; started = now; }
        } else next = shot.sample(elapsed);
        Vec focus = shot.focus(sampledSeconds);
        if (!valid(next, focus, current.position(), scene)) return end();
        current = next;
        currentFocus = focus;
        return current;
    }

    private boolean valid(Pose pose, Vec focus, Vec previous, Scene view) {
        Geometry environment = geometry.forScene(view);
        return Scene.inRange(pose.position(), subject) && environment.clear(previous, pose.position())
            && environment.visible(pose.position(), focus);
    }

    private Pose end() { ended = true; fade = 0; return null; }

    private long timeline(long now) {
        if (lastUpdate != Long.MIN_VALUE) {
            if (now < lastUpdate) end();
            // A client hitch pauses the take instead of skipping its path or exiting the camera.
            else if (now - lastUpdate > 1_000_000_000L) clockOffset += now - lastUpdate;
        }
        lastUpdate = now;
        return now - clockOffset;
    }
    public boolean ended() { return ended; }
    public boolean showing() { return (shot != null || pending != null) && !ended; }
    public Pose current() { return current; }
    public Vec focus() {
        return activity == Scene.Kind.FISHING && scene != null && currentFocus != null
            ? currentFocus.add(fishingTarget.subtract(scene.focus()).scale(FishingSearch.weight(shot.type()))) : currentFocus;
    }
    public void fishing(Vec target) {
        if (activity != Scene.Kind.FISHING) return;
        fishingTarget = target;
    }
    public int take() { return take; }
    public boolean capturing() { return captures > 0; }
    public void bearing(double value) { if (Double.isFinite(value)) bearing = value; }
    public double fade() { return fade; }
    public double lensStrength() { return ended ? 0 : lensStrength; }
    public void coveredFrame() { if (fade == 1) covered = true; }
    public boolean scenery() { return scene != null && scene.scenery(); }
    public boolean underwater() { return scene != null && scene.kind() == Scene.Kind.WATER; }
    public boolean settled() { return showing() && phase == Phase.SHOT; }
    public void nextShot() { if (!ended && !heldForNext) advanceRequested = true; }

    public void beginCapture(long now) {
        if (captures++ == 0) captureStarted = now;
    }

    public void endCapture(long now) {
        if (captures == 0 || --captures > 0) return;
        long elapsed = Math.max(0, now - captureStarted);
        started += elapsed;
        if (handoff != 0) handoff += elapsed;
        lastUpdate = now;
    }

    private void setScene(Scene next) {
        take++;
        scene = next;
        history.add(scene, shot);
    }

    private ShotPlanner planner(Pose from) {
        return new ShotPlanner(subject, from, entry, bearing, config, history, random, activity, fishingTarget);
    }
}
