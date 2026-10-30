package dev.ohneb.afkcinematics.core;

/** Plans in subject coordinates, then validates the actual world-space camera movement. */
public final class MovingGeometry implements Geometry {
    private enum Recovery { NONE, ORIGIN, DESTINATION }
    private final Geometry world;
    private Vec origin, target;
    private Pose previous;
    private Vec previousFocus;
    private double previousFade;
    private int previousTake;
    private long updated;
    private double heading, desiredHeading, turnVelocity;
    private boolean followsCourse, reframeRequested;
    private double elapsed, arm = Double.POSITIVE_INFINITY, reveal = 1;
    private Vec previousTarget;
    private Recovery recovery = Recovery.NONE;
    private boolean recoveryCovered;

    public MovingGeometry(Geometry world, Vec origin, long now) {
        this.world = world;
        this.origin = this.target = origin;
        updated = now;
    }

    public boolean follow(Vec next, long now, boolean capture) {
        if (now < updated || next.distance(target) > 16 || next.distance(origin) > 24) return false;
        elapsed = capture ? 0 : Math.min(0.1, (now - updated) / 1e9);
        target = next;
        updated = now;
        if (!capture) {
            origin = origin.lerp(next, 1 - Math.exp(-elapsed / 0.08));
            turn(elapsed);
        }
        return true;
    }

    public void course(double degrees) {
        if (!Double.isFinite(degrees)) return;
        desiredHeading = degrees;
        if (!followsCourse) { heading = degrees; followsCourse = true; }
    }

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
        Vec worldFocus = focus == null ? null : rotate(focus, heading).add(target);
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
        Vec offset = desired.subtract(target);
        double distance = offset.length();
        Vec point = null;
        if (distance > .001) {
            Vec direction = offset.scale(1 / distance);
            double maximum = Math.min(distance, cut ? distance : arm + elapsed * 2);
            point = along(direction, maximum);
            if (point == null) {
                // A corner can block the old viewing direction completely; try the new course nearby.
                for (int side : new int[]{0, 180, 90, -90}) {
                    Vec nearby = rotate(new Vec(0, .2, -1.25), desiredHeading + side).add(target);
                    if (usable(nearby)) { point = nearby; break; }
                }
            }
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
            double movement = previousTarget == null ? 0 : target.distance(previousTarget);
            double limit = Math.max(.35, movement * 1.5 + elapsed * 12);
            double travel = from.distance(point);
            Vec step = travel > limit ? from.lerp(point, limit / travel) : point;
            if (usable(step) && world.clear(from, step)) point = step;
            else {
                // Do not jump through a trunk. Cover the old view, then recheck a close destination.
                recovery = Recovery.ORIGIN;
                recoveryCovered = false;
                return coveredOrigin();
            }
        }
        double ratio = Math.min(1, point.distance(target) / Math.max(.001, distance));
        Vec aim = target.lerp(focus, ratio);
        if (!world.visible(point, aim)) aim = target;
        Pose result = Pose.lookingAt(point, aim);
        arm = point.distance(target);
        previous = result;
        previousFocus = aim;
        previousTarget = target;
        previousFade = fade;
        previousTake = take;
        if (destination) { recovery = Recovery.DESTINATION; recoveryCovered = false; }
        else if (recovery == Recovery.NONE) reveal = Math.min(1, reveal + elapsed / .3);
        return result;
    }

    private Vec along(Vec direction, double maximum) {
        double minimum = Math.min(.8, maximum);
        Vec far = target.add(direction.scale(maximum));
        if (usable(far)) return far;
        Vec near = target.add(direction.scale(minimum));
        if (!usable(near)) return null;
        // Fixed work per frame; the padded arm also leaves room before foliage reaches the camera.
        for (int i = 0; i < 6; i++) {
            double middle = (minimum + maximum) * .5;
            Vec candidate = target.add(direction.scale(middle));
            if (usable(candidate)) { minimum = middle; near = candidate; }
            else maximum = middle;
        }
        return near;
    }

    private boolean usable(Vec point) {
        return Scene.inRange(point, target) && world.clear(point, point) && world.visible(point, target, .3);
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
