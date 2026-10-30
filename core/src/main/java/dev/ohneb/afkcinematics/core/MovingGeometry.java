package dev.ohneb.afkcinematics.core;

/** Plans in subject coordinates, then validates the actual world-space camera movement. */
public final class MovingGeometry implements Geometry {
    private final Geometry world;
    private Vec origin, target;
    private Pose previous;
    private Vec previousFocus;
    private double previousFade;
    private int previousTake;
    private long updated;
    private double heading, desiredHeading, turnVelocity;
    private boolean followsCourse, reframeRequested;

    public MovingGeometry(Geometry world, Vec origin, long now) {
        this.world = world;
        this.origin = this.target = origin;
        updated = now;
    }

    public boolean follow(Vec next, long now, boolean capture) {
        if (now < updated || next.distance(target) > 16 || next.distance(origin) > 24) return false;
        double elapsed = Math.min(0.1, (now - updated) / 1e9);
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
