package dev.ohneb.afkcinematics.core;

import java.util.Random;

public final class CinematicSession {
    private final Geometry geometry;
    private final Vec subject;
    private final double bearing;
    private Scene.Kind activity;
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
    private long shotStarted, handoff, clockOffset, retryAt;
    private long lastUpdate = Long.MIN_VALUE;
    private long captureStarted;
    private int captures, take;
    private Vec fishingTarget, activeFishingTarget;
    private boolean changingActivity, ended, advanceRequested, manualAdvance;

    public CinematicSession(Vec subject, Pose entry, double bearing, CinematicConfig config, Geometry geometry, Random random) {
        this(subject, entry, bearing, config, geometry, random, Scene.Kind.PLAYER);
    }

    public CinematicSession(Vec subject, Pose entry, double bearing, CinematicConfig config, Geometry geometry, Random random, Scene.Kind activity) {
        this(subject, entry, bearing, config, geometry, random, activity, null);
    }

    public CinematicSession(Vec subject, Pose entry, double bearing, CinematicConfig config, Geometry geometry, Random random, Scene.Kind activity, Vec bobber) {
        this.subject = subject;
        this.entry = this.current = entry;
        this.bearing = bearing;
        this.config = config;
        this.geometry = geometry;
        this.activity = activity;
        this.fishingTarget = this.activeFishingTarget = bobber;
        this.random = random;
        planner = planner();
    }

    public void tick(long now) {
        if (ended || capturing()) return;
        now = timeline(now);
        if (ended) return;
        if (shot != null) {
            if (scene.animal() != null && !geometry.available(scene, current.position())) advanceRequested = true;
            if (advanceRequested && !manualAdvance) {
                planner = planner();
                pending = null;
                manualAdvance = true;
                advanceRequested = false;
                handoff = now;
            } else if (planner == null && pending == null && now >= retryAt
                && (now - shotStarted) / 1e9 >= shot.duration()
                    - (activity == Scene.Kind.PLAYER ? ShotPlanner.preparationSeconds(config) : 4)) {
                planner = planner();
                handoff = shotStarted + Math.round(shot.duration() * 1e9);
            }
        }
        if (planner == null) return;
        for (int i = 0; i < ShotPlanner.stepsPerTick(config) && !planner.exhausted(); i++) {
            var plan = planner.next(geometry);
            if (plan == null) continue;
            pending = plan;
            planner = null;
            return;
        }
        if (planner.exhausted()) {
            if (adaptiveTravel() && shot != null) keepTravel(now);
            else if (shot == null || manualAdvance || now >= handoff) end();
        }
    }

    public Pose frame(long now) {
        if (ended) return null;
        if (capturing()) return shot == null ? null : valid(current, currentFocus, current.position(), scene) ? current : end();
        now = timeline(now);
        if (ended) return null;
        boolean turning = !manualAdvance && activity.travel() && geometry instanceof MovingGeometry moving && moving.turning();
        if (pending != null && (shot == null || now >= handoff && !turning)) {
            var plan = pending;
            if (!geometry.available(plan.scene(), plan.shot().start())) {
                pending = null;
                planner = planner();
            } else {
                Shot destination = aimed(plan.shot(), plan.scene());
                Pose next = destination.sample(0);
                // A cut has no connecting path. Revalidate its own volume, path and sightline here.
                if (!geometry.forScene(plan.scene()).valid(destination, next.position())
                    || !valid(next, destination.focus(0), next.position(), plan.scene())) {
                    if (adaptiveTravel() && shot != null) keepTravel(now);
                    else return end();
                } else {
                    shot = plan.shot();
                    scene = plan.scene();
                    pending = null;
                    changingActivity = manualAdvance = false;
                    activeFishingTarget = fishingTarget;
                    shotStarted = now;
                    take++;
                    history.add(scene, shot);
                    current = next;
                    currentFocus = destination.focus(0);
                    return current;
                }
            }
        }
        if (shot == null) return null;
        Shot active = aimed(shot, scene);
        double seconds = (now - shotStarted) / 1e9;
        Pose next = active.sample(seconds);
        Vec focus = active.focus(seconds);
        if (!valid(next, focus, current.position(), scene)) return end();
        current = next;
        currentFocus = focus;
        return current;
    }

    private boolean valid(Pose pose, Vec focus, Vec previous, Scene view) {
        // MovingGeometry validates the final transformed view, including held captures.
        if (adaptiveTravel()) return Scene.inRange(pose.position(), subject);
        Geometry environment = geometry.forScene(view);
        return Scene.inRange(pose.position(), subject) && environment.clear(previous, pose.position())
            && environment.visible(pose.position(), focus);
    }

    private boolean adaptiveTravel() {
        return activity != Scene.Kind.PLAYER && geometry instanceof MovingGeometry moving && moving.adaptsTravel();
    }

    private void keepTravel(long now) {
        planner = null;
        pending = null;
        manualAdvance = advanceRequested = false;
        retryAt = now + 2_000_000_000L;
    }

    private Shot aimed(Shot candidate, Scene view) {
        if (view == null || view.kind() != Scene.Kind.FISHING) return candidate;
        Vec target = view == scene ? activeFishingTarget : fishingTarget;
        if (target == null) return candidate;
        Vec shift = target.subtract(view.focus()).scale(FishingSearch.weight(candidate.type()));
        return new Shot(candidate.type(), candidate.start(), candidate.control(), candidate.end(),
            candidate.focusStart().add(shift), candidate.focusEnd().add(shift), candidate.duration());
    }

    private Pose end() { ended = true; return null; }

    private long timeline(long now) {
        if (lastUpdate != Long.MIN_VALUE) {
            if (now < lastUpdate) end();
            else if (now - lastUpdate > 1_000_000_000L) clockOffset += now - lastUpdate;
        }
        lastUpdate = now;
        return now - clockOffset;
    }

    public boolean ended() { return ended; }
    public boolean showing() { return shot != null && !ended; }
    public Pose current() { return current; }
    public Vec focus() { return currentFocus; }
    public void fishing(Vec target) {
        if (activity != Scene.Kind.FISHING) return;
        fishingTarget = target;
        if (!changingActivity) activeFishingTarget = target;
    }
    public boolean fishingView() { return scene != null && scene.kind() == Scene.Kind.FISHING && !changingActivity; }

    public void activity(Scene.Kind next, Vec bobber) {
        if (!settled() || capturing()) return;
        activity = next;
        fishingTarget = bobber;
        changingActivity = true;
        manualAdvance = false;
        advanceRequested = true;
    }
    public int take() { return take; }
    public boolean capturing() { return captures > 0; }
    public double lensStrength() { return showing() ? 1 : 0; }
    public boolean scenery() { return scene != null && scene.scenery(); }
    public boolean underwater() { return scene != null && scene.kind() == Scene.Kind.WATER; }
    public boolean settled() { return showing(); }
    public void nextShot() { if (!ended && !manualAdvance) advanceRequested = true; }

    public void beginCapture(long now) {
        if (captures++ == 0) captureStarted = now;
    }

    public void endCapture(long now) {
        if (captures == 0 || --captures > 0) return;
        long elapsed = Math.max(0, now - captureStarted);
        shotStarted += elapsed;
        if (handoff != 0) handoff += elapsed;
        retryAt += elapsed;
        lastUpdate = now;
    }

    private ShotPlanner planner() {
        return new ShotPlanner(subject, current, entry, bearing, config, history, random, activity, fishingTarget);
    }
}
