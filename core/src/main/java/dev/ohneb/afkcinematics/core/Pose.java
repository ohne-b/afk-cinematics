package dev.ohneb.afkcinematics.core;

public record Pose(Vec position, double yaw, double pitch) {
    public Pose {
        if (position == null || !Double.isFinite(yaw) || !Double.isFinite(pitch))
            throw new IllegalArgumentException("Invalid camera pose");
    }

    public static Pose lookingAt(Vec position, Vec target) {
        Vec direction = target.subtract(position);
        double horizontal = Math.hypot(direction.x(), direction.z());
        return new Pose(position, Math.toDegrees(Math.atan2(-direction.x(), direction.z())),
            -Math.toDegrees(Math.atan2(direction.y(), horizontal)));
    }

    public Pose blend(Pose other, double progress) {
        double t = ease(progress);
        double turn = Math.IEEEremainder(other.yaw - yaw, 360);
        return new Pose(position.lerp(other.position, t), yaw + turn * t, pitch + (other.pitch - pitch) * t);
    }

    public static double ease(double t) {
        t = Math.clamp(t, 0, 1);
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
}
