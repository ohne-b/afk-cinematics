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
        if (!capture) origin = origin.lerp(next, 1 - Math.exp(-elapsed / 0.08));
        return true;
    }

    public Pose local(Pose pose) { return new Pose(pose.position().subtract(origin), pose.yaw(), pose.pitch()); }
    public boolean sees(Pose pose, Vec focus) { return world.visible(pose.position(), focus.add(target)); }

    public Pose apply(Pose pose, Vec focus, double fade, int take, boolean capture) {
        if (pose == null) return null;
        Vec point = pose.position().add(origin);
        Vec worldFocus = focus == null ? null : focus.add(target);
        Pose result = worldFocus == null ? new Pose(point, pose.yaw(), pose.pitch()) : Pose.lookingAt(point, worldFocus);
        if (capture && previous != null) { result = previous; worldFocus = previousFocus; }
        boolean cut = take != previousTake && fade == 1 && previousFade == 1;
        Vec from = previous == null || cut ? result.position() : previous.position();
        if (!Scene.inRange(result.position(), target) || !world.clear(from, result.position())
            || worldFocus != null && !world.visible(result.position(), worldFocus)) return null;
        previous = result;
        previousFocus = worldFocus;
        previousFade = fade;
        previousTake = take;
        return result;
    }

    @Override public boolean clear(Vec from, Vec to, double margin) { return world.clear(from.add(origin), to.add(origin), margin); }
    @Override public boolean visible(Vec from, Vec to, double margin) { return world.visible(from.add(origin), to.add(origin), margin); }
    @Override public double viewQuality(Pose view) {
        return world.viewQuality(new Pose(view.position().add(origin), view.yaw(), view.pitch()));
    }
}
