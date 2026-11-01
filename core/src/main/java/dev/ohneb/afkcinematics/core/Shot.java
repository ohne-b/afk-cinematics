package dev.ohneb.afkcinematics.core;

public record Shot(Type type, Vec start, Vec control, Vec end, Vec focusStart, Vec focusEnd, double duration) {
    public enum Type {
        ARC, DOLLY, DRIFT, CRANE, OVERHEAD, FLYOVER, LOW_PASS, REVEAL, FLY_THROUGH, TRAVERSE, PAN, STILL,
        DIAGONAL_GLIDE, RISING_PASS, DESCENDING_PASS, PULL_AWAY, TRAIL, ESCORT, LEAD, WIDE_TRACK,
        ANGLER, CAST_LINE, FLOAT, LOW_ESCORT, HIGH_TRAIL, BROADSIDE, PASS_BY, OVERLOOK;
        public boolean fixedTravel() { return this == PASS_BY || this == OVERLOOK; }
        public boolean fishingOnly() { return this == ANGLER || this == CAST_LINE || this == FLOAT; }
        public boolean travelOnly() {
            return this == TRAIL || this == ESCORT || this == LEAD || this == WIDE_TRACK
                || this == LOW_ESCORT || this == HIGH_TRAIL || this == BROADSIDE || fixedTravel();
        }
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

    /** Keep the original movement speed, but cut before the full path finishes. */
    public Shot cutAfter(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > duration)
            throw new IllegalArgumentException("Invalid cut time");
        double t = seconds / duration;
        Vec left = start.lerp(control, t), right = control.lerp(end, t);
        return new Shot(type, start, left, left.lerp(right, t),
            focusStart, focus(seconds), seconds);
    }

    private double progress(double seconds) {
        return Math.clamp(seconds / duration, 0, 1);
    }
}
