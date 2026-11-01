package dev.ohneb.afkcinematics.core;

import java.util.Random;

public final class CinematicSession {
    private static final double FADE_OUT_SECONDS = .35;
    private static final double FADE_IN_SECONDS = .45;
    private enum Phase { ENTRY, SHOT, BLEND, FADE_OUT, CUT, FADE_IN }
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
    private Shot blend;
    private Scene scene;
    private final ShotHistory history = new ShotHistory();
    private Pose current;
    private Vec currentFocus;
    private long started, shotStarted, handoff, clockOffset, retryAt;
    private long lastUpdate = Long.MIN_VALUE;
    private long captureStarted;
    private int captures;
    private int take;
    private Vec fishingTarget;
    private Vec activeFishingTarget;
    private boolean changingActivity;
    private Phase phase = Phase.ENTRY;
    private boolean ended;
    private boolean covered;
    private boolean advanceRequested;
    private boolean manualAdvance;
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
        this.activeFishingTarget = bobber;
        this.random = random;
        planner = planner(current);
    }

    public void tick(long now) {
        if (ended || captures > 0) return;
        now = timeline(now);
        if (ended) return;
        if (shot != null && phase == Phase.SHOT) {
            if (scene.animal() != null && !geometry.available(scene, current.position()))
                advanceRequested = true;
            if (advanceRequested && !manualAdvance) {
                planner = planner(current);
                pending = null;
                manualAdvance = true;
                handoff = now;
                advanceRequested = false;
            } else if (planner == null && pending == null && now >= retryAt
                && (now - shotStarted) / 1e9 >= shot.duration() - roll(shot) - FADE_OUT_SECONDS
                    - (activity == Scene.Kind.PLAYER ? ShotPlanner.preparationSeconds(config) : 4)) {
                planner = planner(shot.sample(shot.duration()));
                handoff = shotStarted + Math.round(shot.duration() * 1e9);
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
                if (adaptiveTravel() && shot != null) { keepTravel(now); return; }
                if (shot == null || manualAdvance || now >= handoff) ended = true;
                return;
            }
        }
        if (pending == null || phase != Phase.ENTRY && phase != Phase.SHOT) return;
        long transition = handoff - (pending.cut() ? Math.round((FADE_OUT_SECONDS + roll(shot)) * 1e9) : 0);
        if (shot != null && phase == Phase.SHOT && now < transition) return;
        if (shot != null && !manualAdvance && activity.travel() && geometry instanceof MovingGeometry moving
            && moving.turning()) return;
        boolean initial = shot == null;
        if (!initial && phase == Phase.SHOT && !manualAdvance && !pending.cut()) {
            Shot active = aimed(shot, scene);
            Pose end = active.sample(active.duration());
            Vec focus = active.focus(active.duration());
            if (!valid(end, focus, current.position(), scene)) { end(); return; }
            current = end;
            currentFocus = focus;
        }
        var plan = pending;
        if (!geometry.available(plan.scene(), plan.shot().start())) {
            pending = null;
            planner = planner(current);
            return;
        }
        if (!geometry.forScene(plan.scene()).valid(aimed(plan.shot(), plan.scene()), plan.cut() ? plan.shot().start() : current.position())) {
            if (adaptiveTravel() && shot != null) keepTravel(now);
            else end();
            return;
        }
        blend = travelBlend(plan);
        if (blend != null) phase = Phase.BLEND;
        else if (plan.cut()) {
            phase = Phase.FADE_OUT;
            covered = false;
        } else {
            shot = plan.shot();
            setScene(plan.scene());
            pending = null;
            shotStarted = now;
            phase = plan.entering() ? Phase.ENTRY : Phase.SHOT;
            if (plan.entering()) {
                double distance = entry.position().distance(shot.start());
                Pose target = shot.sample(0);
                double turn = Math.max(Math.abs(Math.IEEEremainder(target.yaw() - entry.yaw(), 360)),
                    Math.abs(target.pitch() - entry.pitch()));
                entrySeconds = Math.clamp(Math.max(distance / 1.5, turn / 45), 1.5, 6);
            }
        }
        manualAdvance = false;
        started = now;
    }

    public Pose frame(long now) {
        if (ended || shot == null && pending == null) return null;
        if (captures > 0) {
            boolean safe = adaptiveTravel() || (currentFocus == null
                ? geometry.forScene(scene).clear(current.position(), current.position())
                : valid(current, currentFocus, current.position(), scene));
            return safe ? current : end();
        }
        now = timeline(now);
        if (ended) return null;
        double elapsed = (now - started) / 1e9;
        if (phase == Phase.BLEND) {
            double progress = Pose.ease(elapsed / blend.duration());
            Vec focus = blend.focusStart().lerp(blend.focusEnd(), progress);
            Pose next = Pose.lookingAt(blend.start().lerp(blend.end(), progress), focus);
            if (!valid(next, focus, current.position(), scene)) return end();
            current = next;
            currentFocus = focus;
            if (progress == 1) {
                shot = pending.shot();
                setScene(pending.scene());
                pending = null;
                blend = null;
                phase = Phase.SHOT;
                started = shotStarted = now;
            }
            return current;
        }
        if (phase == Phase.FADE_OUT) {
            Pose next = current;
            Vec focus = currentFocus;
            if (shot != null) {
                Shot outgoing = aimed(shot, scene);
                double seconds = (now - shotStarted) / 1e9;
                next = outgoing.sample(seconds);
                focus = outgoing.focus(seconds);
            }
            if (focus == null ? !adaptiveTravel() && !geometry.forScene(scene).clear(current.position(), next.position())
                : !valid(next, focus, current.position(), scene)) return end();
            current = next;
            currentFocus = focus;
            fade = Pose.ease(elapsed / FADE_OUT_SECONDS);
            // Render the old viewpoint fully covered before changing its position.
            if (fade == 1) phase = Phase.CUT;
            return current;
        }
        if (phase == Phase.CUT) {
            if (!covered) return adaptiveTravel() || geometry.forScene(scene).clear(current.position(), current.position()) ? current : end();
            if (!geometry.available(pending.scene(), pending.shot().start())) {
                // Reveal the previous take again if the animal left during the cover.
                // Continue from its endpoint rather than rewinding its movement.
                pending = null;
                planner = null;
                phase = Phase.FADE_IN;
                shot = new Shot(shot.type(), current.position(), current.position(), current.position(),
                    currentFocus, currentFocus, shot.duration());
                started = shotStarted = now;
                advanceRequested = true;
                return current;
            }
            Shot destination = aimed(pending.shot(), pending.scene());
            double lead = roll(destination);
            Pose next = destination.sample(lead);
            if (!valid(next, destination.focus(lead), next.position(), pending.scene())) return end();
            shot = pending.shot();
            setScene(pending.scene());
            pending = null;
            current = next;
            currentFocus = destination.focus(lead);
            lensStrength = 1;
            phase = Phase.FADE_IN;
            started = now;
            shotStarted = now - Math.round(lead * 1e9);
            fade = 1;
            covered = false;
            return current;
        }
        Pose next;
        Shot active = aimed(shot, scene);
        double sampledSeconds = (now - shotStarted) / 1e9;
        if (phase == Phase.ENTRY) {
            sampledSeconds = 0;
            next = entry.blend(active.sample(0), elapsed / entrySeconds);
            lensStrength = Pose.ease(elapsed / entrySeconds);
            if (elapsed >= entrySeconds) { phase = Phase.SHOT; shotStarted = started + Math.round(entrySeconds * 1e9); }
        } else if (phase == Phase.FADE_IN) {
            if (!covered) {
                shotStarted += now - started;
                started = now;
            }
            elapsed = (now - started) / 1e9;
            sampledSeconds = (now - shotStarted) / 1e9;
            next = active.sample(sampledSeconds);
            fade = 1 - Pose.ease(elapsed / FADE_IN_SECONDS);
            if (fade == 0) phase = Phase.SHOT;
        } else next = active.sample(sampledSeconds);
        Vec focus = active.focus(sampledSeconds);
        if (!valid(next, focus, current.position(), scene)) return end();
        current = next;
        currentFocus = focus;
        return current;
    }

    private Shot travelBlend(ShotPlanner.Plan plan) {
        if (shot == null || changingActivity || !activity.travel() || scene.kind() != plan.scene().kind()
            || config.motion != CinematicConfig.Motion.GENTLE || currentFocus == null
            || !(geometry instanceof MovingGeometry moving) || !moving.steadyCourse()) return null;
        Pose next = plan.shot().sample(0);
        double distance = current.position().distance(next.position());
        double turn = Math.abs(Math.IEEEremainder(next.yaw() - current.yaw(), 360));
        if (distance > 8 || turn > 65 || Math.abs(next.pitch() - current.pitch()) > 25) return null;
        Vec from = current.position().subtract(subject), path = next.position().subtract(current.position());
        double length = path.x() * path.x() + path.z() * path.z();
        double closest = length == 0 ? 0 : Math.clamp(-(from.x() * path.x() + from.z() * path.z()) / length, 0, 1);
        Vec nearest = from.add(path.scale(closest));
        if (Math.hypot(nearest.x(), nearest.z()) < 2.5) return null;
        double duration = Math.clamp(Math.max(distance / 2, turn / 25), 1.2, 4);
        Shot bridge = new Shot(plan.shot().type(), current.position(), current.position().lerp(next.position(), .5),
            next.position(), currentFocus, plan.shot().focus(0), duration);
        return geometry.forScene(scene).valid(bridge, current.position()) ? bridge : null;
    }

    // Reveal a moving take after its slow lead-in, and cover it before its slow lead-out.
    private double roll(Shot shot) {
        if (shot == null || shot.start().equals(shot.control()) && shot.start().equals(shot.end())
            && shot.focusStart().equals(shot.focusEnd())) return 0;
        double preparation = activity == Scene.Kind.PLAYER ? ShotPlanner.preparationSeconds(config) : 4;
        // Leave both fades and the complete planning budget inside short takes.
        return Math.clamp((shot.duration() - preparation - FADE_OUT_SECONDS - FADE_IN_SECONDS - .05) / 2, 0, .75);
    }

    private boolean valid(Pose pose, Vec focus, Vec previous, Scene view) {
        // MovingGeometry resolves travel framing and validates the applied world-space sweep afterward.
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
        manualAdvance = false;
        advanceRequested = false;
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
    public double fade() { return fade; }
    public double lensStrength() { return ended ? 0 : lensStrength; }
    public void coveredFrame() { if (fade == 1) covered = true; }
    public boolean scenery() { return scene != null && scene.scenery(); }
    public boolean underwater() { return scene != null && scene.kind() == Scene.Kind.WATER; }
    public boolean settled() { return showing() && phase == Phase.SHOT; }
    public void nextShot() { if (!ended && !manualAdvance) advanceRequested = true; }

    public void beginCapture(long now) {
        if (captures++ == 0) captureStarted = now;
    }

    public void endCapture(long now) {
        if (captures == 0 || --captures > 0) return;
        long elapsed = Math.max(0, now - captureStarted);
        started += elapsed;
        shotStarted += elapsed;
        if (handoff != 0) handoff += elapsed;
        lastUpdate = now;
    }

    private void setScene(Scene next) {
        take++;
        scene = next;
        changingActivity = false;
        activeFishingTarget = fishingTarget;
        history.add(scene, shot);
    }

    private ShotPlanner planner(Pose from) {
        return new ShotPlanner(subject, from, entry, bearing, config, history, random, activity, fishingTarget);
    }
}
