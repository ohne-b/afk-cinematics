package dev.ohneb.afkcinematics.core;

public record Shot(Type type, Vec start, Vec control, Vec end, Vec focusStart, Vec focusEnd, double duration) {
    public enum Type {
        ARC, DOLLY, DRIFT, CRANE, OVERHEAD, FLYOVER, LOW_PASS, REVEAL, FLY_THROUGH, TRAVERSE, PAN, STILL,
        DIAGONAL_GLIDE, RISING_PASS, DESCENDING_PASS, PULL_AWAY;
        public boolean sceneryOnly() {
            return this == OVERHEAD || this == FLY_THROUGH || this == TRAVERSE
                || this == DIAGONAL_GLIDE || this == RISING_PASS || this == DESCENDING_PASS || this == PULL_AWAY;
        }
    }

    public Shot {
        if (type == null || start == null || control == null || end == null || focusStart == null || focusEnd == null
            || !Double.isFinite(duration) || duration <= 0) throw new IllegalArgumentException("Invalid shot");
    }

    public Pose sample(double seconds) {
        double t = progress(seconds);
        Vec position = start.scale((1 - t) * (1 - t))
            .add(control.scale(2 * (1 - t) * t)).add(end.scale(t * t));
        return Pose.lookingAt(position, focusStart.lerp(focusEnd, t));
    }

    public Vec focus(double seconds) { return focusStart.lerp(focusEnd, progress(seconds)); }

    private double progress(double seconds) {
        seconds = Math.clamp(seconds, 0, duration);
        double ramp = Math.min(2, duration * 0.2);
        if (seconds > duration - ramp) return 1 - progress(duration - seconds);
        if (seconds < ramp)
            return (seconds - ramp / Math.PI * Math.sin(Math.PI * seconds / ramp)) / (2 * (duration - ramp));
        return (seconds - ramp * 0.5) / (duration - ramp);
    }
}
