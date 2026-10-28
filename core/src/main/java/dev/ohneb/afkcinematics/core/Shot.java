package dev.ohneb.afkcinematics.core;

public record Shot(Type type, Vec subject, Vec start, Vec control, Vec end, double duration) {
    public enum Type { ARC, DOLLY, DRIFT, STILL }

    public Pose sample(double seconds) {
        double t = Pose.ease(seconds / duration);
        Vec position = start.scale((1 - t) * (1 - t))
            .add(control.scale(2 * (1 - t) * t)).add(end.scale(t * t));
        return Pose.lookingAt(position, subject);
    }
}
