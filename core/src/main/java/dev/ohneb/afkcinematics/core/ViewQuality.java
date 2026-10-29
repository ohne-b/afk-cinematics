package dev.ohneb.afkcinematics.core;

/** Ranks a three-by-three depth grid; safety is checked separately. */
public final class ViewQuality {
    public static final double RANGE = 32;
    private ViewQuality() { }

    public static double score(double[] depth, double pitch, boolean outdoors) {
        if (depth.length != 9) throw new IllegalArgumentException("Expected nine view samples");
        if (!Double.isFinite(pitch)) return 0;
        int unknown = 0, near = 0, distant = 0;
        double open = 0, middle = 0, nearest = RANGE, farthest = 0;
        for (int i = 0; i < depth.length; i++) {
            double distance = depth[i];
            if (!Double.isFinite(distance) || distance < 0) { unknown++; continue; }
            distance = Math.min(distance, RANGE);
            if (distance < 3) near++;
            if (distance == RANGE) distant++;
            if (i >= 3 && i <= 5) middle += distance / (3 * RANGE);
            open += Math.log1p(distance) / Math.log1p(RANGE);
            nearest = Math.min(nearest, distance);
            farthest = Math.max(farthest, distance);
        }
        if (unknown > 2 || depth[4] < 3 || !Double.isFinite(depth[4]) || near > 4) return 0;
        double layers = Math.min(1, (farthest - nearest) / 20);
        double steep = outdoors ? Math.max(0, Math.abs(pitch) - 22) / 25 : 0;
        return Math.max(0, open * 5 / 9 + middle * 3 + layers * 2 - near - unknown - steep
            - Math.max(0, distant - 5) * 0.75);
    }

    public static Vec[] directions(Pose pose, double verticalFov, double aspect, boolean letterbox) {
        double yaw = Math.toRadians(pose.yaw()), pitch = Math.toRadians(pose.pitch());
        Vec forward = pose.lookAhead(1).subtract(pose.position());
        Vec right = new Vec(Math.cos(yaw), 0, Math.sin(yaw));
        Vec up = new Vec(-Math.sin(pitch) * Math.sin(yaw), Math.cos(pitch), Math.sin(pitch) * Math.cos(yaw));
        double slope = Math.tan(Math.toRadians(verticalFov) / 2);
        Vec[] rays = new Vec[9];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                Vec ray = forward.add(right.scale((column - 1) * slope * aspect * 0.65))
                    .add(up.scale((1 - row) * slope * 0.65 * (letterbox ? 0.82 : 1)));
                rays[row * 3 + column] = ray.scale(1 / ray.length());
            }
        }
        return rays;
    }
}
