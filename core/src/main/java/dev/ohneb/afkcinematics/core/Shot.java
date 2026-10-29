package dev.ohneb.afkcinematics.core;

public record Shot(Type type, Vec start, Vec control, Vec end, Vec focusStart, Vec focusEnd, double duration) {
    public enum Type { ARC, DOLLY, DRIFT, CRANE, OVERHEAD, PAN, STILL }

    public Shot {
        if (type == null || start == null || control == null || end == null || focusStart == null || focusEnd == null
            || !Double.isFinite(duration) || duration <= 0) throw new IllegalArgumentException("Invalid shot");
    }

    public Pose sample(double seconds) {
        double t = Pose.ease(seconds / duration);
        Vec position = start.scale((1 - t) * (1 - t))
            .add(control.scale(2 * (1 - t) * t)).add(end.scale(t * t));
        return Pose.lookingAt(position, focus(seconds));
    }

    public Vec focus(double seconds) { return focusStart.lerp(focusEnd, Pose.ease(seconds / duration)); }
}
