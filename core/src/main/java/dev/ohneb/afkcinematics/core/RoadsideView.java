package dev.ohneb.afkcinematics.core;

/** A planted camera: only its head moves. Framing includes the subject's body and letterbox crop. */
final class RoadsideView {
    private Pose pose;

    RoadsideView(Vec position, Vec focus) { pose = Pose.lookingAt(position, focus); }

    Pose sample(Vec focus, double seconds) {
        Pose aim = Pose.lookingAt(pose.position(), focus);
        double blend = 1 - Math.exp(-seconds * 6);
        double yaw = Math.IEEEremainder(aim.yaw() - pose.yaw(), 360);
        pose = new Pose(pose.position(), pose.yaw() + Math.clamp(yaw * blend, -45 * seconds, 45 * seconds),
            pose.pitch() + Math.clamp((aim.pitch() - pose.pitch()) * blend, -30 * seconds, 30 * seconds));
        return pose;
    }

    static boolean fits(Pose view, Vec subject, double width, double height, double fov, double aspect, double crop, double fill) {
        Vec offset = subject.subtract(view.position());
        double yaw = Math.toRadians(view.yaw()), pitch = Math.toRadians(view.pitch());
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        double depth = -sy * cp * offset.x() - sp * offset.y() + cy * cp * offset.z();
        double horizontal = cy * offset.x() + sy * offset.z();
        double vertical = -sy * sp * offset.x() + cp * offset.y() + cy * sp * offset.z();
        double usableDepth = depth - Math.max(width, height);
        double halfHeight = usableDepth * Math.tan(Math.toRadians(fov) * .5);
        return usableDepth > 1 && Math.abs(horizontal) + width < halfHeight * aspect * fill
            && Math.abs(vertical) + height < halfHeight * crop * fill;
    }
}
