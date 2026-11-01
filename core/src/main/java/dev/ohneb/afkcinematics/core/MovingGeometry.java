package dev.ohneb.afkcinematics.core;

/** Plans in subject coordinates, then validates the actual world-space camera movement. */
public final class MovingGeometry implements Geometry {
    private enum Recovery { NONE, ORIGIN, DESTINATION }
    private final Geometry world;
    private final FollowMotion motion;
    private Scene.Kind activity = Scene.Kind.FLIGHT;
    private boolean frontViews = true;
    private Vec origin, target;
    private Pose previous;
    private Vec previousFocus;
    private double previousFade;
    private int previousTake;
    private long updated;
    private double heading, desiredHeading, turnVelocity;
    private boolean followsCourse, reframeRequested;
    private double elapsed, extension = 1, reveal = 1;
    private Vec previousTarget, previousOrigin;
    private Recovery recovery = Recovery.NONE;
    private boolean recoveryCovered;

    public MovingGeometry(Geometry world, Vec origin, long now) {
        this.world = world;
        this.origin = this.target = origin;
        motion = new FollowMotion(origin);
        updated = now;
    }

    public void framing(Scene.Kind activity, boolean frontViews) {
        this.activity = activity;
        this.frontViews = frontViews;
    }

    public boolean follow(Vec next, long now, boolean capture, boolean grounded) {
        if (now < updated || next.distance(target) > 16 || next.distance(origin) > 24) return false;
        elapsed = capture ? 0 : Math.min(0.25, (now - updated) / 1e9);
        target = next;
        updated = now;
        if (!capture) {
            if (followsCourse && activity.travel())
                origin = motion.follow(next, elapsed, activity == Scene.Kind.FOLLOW || activity == Scene.Kind.RIDE, grounded);
            else {
                origin = origin.lerp(next, 1 - Math.exp(-elapsed / 0.08));
                motion.reset(origin);
            }
            turn(elapsed);
        } else motion.hold(next);
        return true;
    }

    public void course(double degrees) {
        if (!Double.isFinite(degrees)) return;
        desiredHeading = degrees;
        if (!followsCourse) { heading = degrees; followsCourse = true; }
    }

    public void holdCourse() { desiredHeading = heading; turnVelocity = 0; }

    public Vec relative(Vec offset) { return rotate(offset, -heading); }

    public boolean needsReframe(Scene.Kind kind) {
        double threshold = kind == Scene.Kind.MINECART ? 135 : 75;
        if (!followsCourse || reframeRequested || Math.abs(Math.IEEEremainder(desiredHeading - heading, 360)) < threshold) return false;
        reframeRequested = true;
        return true;
    }

    private void turn(double seconds) {
        if (!followsCourse || seconds <= 0) return;
        double change = Math.IEEEremainder(desiredHeading - heading, 360);
        double offset = -change;
        double spring = 3.5;
        double temporary = (turnVelocity + spring * offset) * seconds;
        double decay = Math.exp(-spring * seconds);
        double step = Math.clamp(change + (offset + temporary) * decay, -22 * seconds, 22 * seconds);
        heading += step;
        turnVelocity = Math.clamp((turnVelocity - spring * temporary) * decay, -22, 22);
    }

    public Pose local(Pose pose) {
        return new Pose(rotate(pose.position().subtract(origin), -heading), pose.yaw() - heading, pose.pitch());
    }

    public boolean sees(Pose pose, Vec focus) { return world.visible(pose.position(), rotate(focus, heading).add(target)); }

    public Pose apply(Pose pose, Vec focus, double fade, int take, boolean capture) {
        if (pose == null) return null;
        boolean cut = take != previousTake && fade == 1 && previousFade == 1;
        if (cut && followsCourse && !capture) {
            heading = desiredHeading;
            turnVelocity = 0;
            reframeRequested = false;
        }
        Vec point = rotate(pose.position(), heading).add(origin);
        Vec worldFocus = focus == null ? null : rotate(focus, heading).add(activity.travel() ? origin : target);
        Pose result = worldFocus == null ? new Pose(point, pose.yaw() + heading, pose.pitch()) : Pose.lookingAt(point, worldFocus);
        if (capture && previous != null) { result = previous; worldFocus = previousFocus; }
        else if (followsCourse && focus != null) return travel(point, worldFocus, fade, take, cut);
        Vec from = previous == null || cut ? result.position() : previous.position();
        if (!Scene.inRange(result.position(), target) || !world.clear(from, result.position())
            || !world.visible(result.position(), target)
            || worldFocus != null && !worldFocus.equals(target) && !world.visible(result.position(), worldFocus)) return null;
        previous = result;
        previousFocus = worldFocus;
        previousFade = fade;
        previousTake = take;
        return result;
    }

    /** Travel framing may shorten, but every revealed position still has a clear rider sightline. */
    private Pose travel(Vec desired, Vec focus, double fade, int take, boolean cut) {
        boolean walking = walking();
        if (walking) desired = walkingView(desired, cut);
        Vec offset = desired.subtract(target);
        double distance = offset.length();
        Vec point = null;
        if (distance > .001) {
            Vec direction = offset.scale(1 / distance);
            // Only obstruction shortens the boom; jumping must not change its intended length.
            double maximum = distance * (cut || walking ? 1 : Math.min(1, extension + elapsed * 2 / Math.max(.8, distance)));
            point = nearby(direction, maximum, walking ? .15 : .3);
            // A rider beside a corner can be inside the padding while the real sightline is clear.
            if (point == null) point = nearby(direction, maximum, 0);
        }
        if (point == null) return null;

        Vec from = previous == null || cut ? point : previous.position();
        if (recovery == Recovery.ORIGIN && !recoveryCovered) return coveredOrigin();
        boolean destination = recovery == Recovery.ORIGIN;
        if (recovery == Recovery.DESTINATION && !recoveryCovered) {
            // The destination must itself be submitted fully covered before revealing it.
            return coveredOrigin();
        }
        if (recovery == Recovery.DESTINATION) { recovery = Recovery.NONE; reveal = 0; recoveryCovered = false; }
        if (!destination && !cut && previous != null) {
            Vec carried = previousOrigin == null ? from : from.add(origin.subtract(previousOrigin));
            double movement = walking ? carried.distance(from) : previousTarget == null ? 0 : target.distance(previousTarget);
            double limit = Math.max(.001, movement * (walking ? 1 : 1.5) + elapsed * (walking ? 6 : 12));
            double travel = from.distance(point);
            Vec step = travel > limit ? from.lerp(point, limit / travel) : point;
            if (walking) {
                // Ease composition changes relative to the filtered rig, not the player's jump arc.
                double blend = 1 - Math.exp(-elapsed * 8);
                double widening = point.distance(origin) - carried.distance(origin);
                if (widening > 0) blend = Math.min(blend, elapsed * 2 / widening);
                Vec eased = carried.lerp(point, blend);
                if (usable(eased, 0) && world.clear(from, eased)) step = eased;
            }
            if (usable(step, 0) && world.clear(from, step)) point = step;
            else {
                // Do not jump through a trunk. Cover the old view, then recheck a close destination.
                recovery = Recovery.ORIGIN;
                recoveryCovered = false;
                return coveredOrigin();
            }
        }
        double ratio = Math.min(1, point.distance(target) / Math.max(.001, distance));
        Vec aim = target.lerp(focus, ratio);
        if (!activity.travel() && !world.visible(point, aim)) aim = target;
        Pose result = Pose.lookingAt(point, aim);
        extension = ratio;
        previous = result;
        previousFocus = aim;
        previousTarget = target;
        previousOrigin = origin;
        previousFade = fade;
        previousTake = take;
        if (destination) { recovery = Recovery.DESTINATION; recoveryCovered = false; }
        else if (recovery == Recovery.NONE) reveal = Math.min(1, reveal + elapsed / .3);
        return result;
    }

    private boolean walking() { return activity == Scene.Kind.FOLLOW || activity == Scene.Kind.RIDE; }

    private Vec walkingView(Vec desired, boolean cut) {
        Vec carried = previous == null || previousOrigin == null || cut ? desired : previous.position().add(origin.subtract(previousOrigin));
        Vec ahead = previousOrigin == null || elapsed <= 0 ? new Vec(0, 0, 0) : origin.subtract(previousOrigin).scale(.3 / elapsed);
        ahead = new Vec(ahead.x(), 0, ahead.z());
        if (ahead.length() > 1.5) ahead = ahead.scale(1.5 / ahead.length());
        if (usable(desired, .3) && bodyVisible(desired) && world.clear(desired, desired.add(ahead), .3)
            && (previous == null || cut || world.clear(previous.position(), desired))) return desired;

        Vec best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        // Keep an established roomy view when a wall or stair edge makes nearby choices alternate.
        if (carried.distance(target) >= Math.min(3.5, desired.distance(target) * .7)) {
            bestScore = walkingScore(carried, desired, carried, ahead, cut, bestScore);
            if (Double.isFinite(bestScore)) best = carried;
        }
        Vec offset = desired.subtract(origin);
        double radius = Math.hypot(offset.x(), offset.z());
        Vec[] arms = {offset, rotate(offset, -20), rotate(offset, 20), rotate(offset, -40), rotate(offset, 40),
            rotate(new Vec(0, offset.y(), -radius), desiredHeading)};
        for (double lift : new double[]{0, .75, 1.5, 3, 4.5, 6}) {
            for (Vec arm : arms) {
                Vec candidate = origin.add(arm).add(new Vec(0, lift, 0));
                double score = walkingScore(candidate, desired, carried, ahead, cut, bestScore);
                if (score < bestScore) { best = candidate; bestScore = score; }
            }
        }
        return best == null ? desired : best;
    }

    private double walkingScore(Vec point, Vec desired, Vec carried, Vec ahead, boolean cut, double bestScore) {
        double deviation = point.distance(desired), change = point.distance(carried);
        double score = deviation * deviation + change * change * .5;
        if (score >= bestScore || !usable(point, .15)) return Double.POSITIVE_INFINITY;
        if (!bodyVisible(point)) score += 64;
        if (score >= bestScore) return Double.POSITIVE_INFINITY;
        // Look a short distance ahead so the rig starts lifting or moving sideways before impact.
        if (!world.clear(point, point.add(ahead), .15)) score += 64;
        if (score >= bestScore) return Double.POSITIVE_INFINITY;
        if (previous != null && !cut && !world.clear(previous.position(), point)) score += 256;
        return score;
    }

    private boolean bodyVisible(Vec point) {
        // Prefer room for the lower body too, so descending one step does not suddenly hide the rider.
        return world.visible(point, new Vec(target.x(), Math.min(target.y(), origin.y()) - .65, target.z()));
    }

    private Vec nearby(Vec direction, double maximum, double margin) {
        Vec point = along(direction, maximum, margin);
        if (point != null) return point;
        // A corner can block the old viewing direction completely; try the new course nearby.
        for (int side : new int[]{0, 180, 90, -90}) {
            Vec candidate = rotate(new Vec(0, .2, -1.25), desiredHeading + side).add(target);
            if (usable(candidate, margin)) return candidate;
        }
        return null;
    }

    private Vec along(Vec direction, double maximum, double margin) {
        double minimum = Math.min(.8, maximum);
        Vec far = target.add(direction.scale(maximum));
        if (usable(far, margin)) return far;
        Vec near = target.add(direction.scale(minimum));
        if (!usable(near, margin)) return null;
        // Fixed work per frame; the padded arm also leaves room before foliage reaches the camera.
        for (int i = 0; i < 10 && maximum - minimum > .01; i++) {
            double middle = (minimum + maximum) * .5;
            Vec candidate = target.add(direction.scale(middle));
            if (usable(candidate, margin)) { minimum = middle; near = candidate; }
            else maximum = middle;
        }
        return near;
    }

    private boolean usable(Vec point, double margin) {
        if (!frontViews && activity.travel()) {
            double radians = Math.toRadians(desiredHeading);
            Vec offset = point.subtract(target);
            if (-Math.sin(radians) * offset.x() + Math.cos(radians) * offset.z() > .05) return false;
        }
        return Scene.inRange(point, target) && world.clear(point, point, margin) && world.visible(point, target, walking() ? 0 : margin);
    }

    private Pose coveredOrigin() {
        return previous != null && Scene.inRange(previous.position(), target)
            && world.clear(previous.position(), previous.position()) ? previous : null;
    }

    public double fade() { return recovery != Recovery.NONE ? 1 : 1 - Pose.ease(reveal); }
    public void coveredFrame() { if (recovery != Recovery.NONE) recoveryCovered = true; }

    public boolean adaptsTravel() { return followsCourse; }

    @Override public boolean clear(Vec from, Vec to, double margin) {
        return world.clear(rotate(from, heading).add(origin), rotate(to, heading).add(origin), margin);
    }
    @Override public boolean visible(Vec from, Vec to, double margin) {
        return world.visible(rotate(from, heading).add(origin), rotate(to, heading).add(target), margin);
    }

    @Override public double viewQuality(Pose view) {
        return world.viewQuality(new Pose(rotate(view.position(), heading).add(origin), view.yaw() + heading, view.pitch()));
    }

    private static Vec rotate(Vec point, double degrees) {
        double angle = Math.toRadians(degrees), cosine = Math.cos(angle), sine = Math.sin(angle);
        return new Vec(point.x() * cosine - point.z() * sine, point.y(), point.x() * sine + point.z() * cosine);
    }
}
