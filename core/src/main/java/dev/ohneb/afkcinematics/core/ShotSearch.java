package dev.ohneb.afkcinematics.core;

import java.util.Random;

/** One candidate per call so world queries can be spread across client ticks. */
public final class ShotSearch {
    private static final double[] ANGLES = {0, 30, -30, 60, -60, 90, -90, 135, -135, 180};
    private final Vec subject;
    private final Pose previous;
    private final double bearing;
    private final double distance;
    private final CinematicConfig.Motion motion;
    private final Shot.Type last;
    private final boolean entering;
    private final Random random;
    private int attempt;

    public ShotSearch(Vec subject, Pose previous, double bearing, double distance,
                      CinematicConfig.Motion motion, Shot.Type last, boolean entering, Random random) {
        this.subject = subject;
        this.previous = previous;
        this.bearing = bearing;
        this.distance = distance;
        this.motion = motion;
        this.last = last;
        this.entering = entering;
        this.random = random;
    }

    public boolean exhausted() { return attempt >= ANGLES.length + 1; }

    public Shot next(Geometry geometry) {
        if (exhausted()) return null;
        int index = attempt++;
        boolean fallback = index == ANGLES.length;
        double angle = Math.toRadians(bearing + ANGLES[Math.min(index, ANGLES.length - 1)]);
        double radius = index < 5 ? distance : Math.min(distance, 2.0);
        Vec start = entering && !fallback
            ? subject.add(new Vec(Math.sin(angle) * radius, 0.55, -Math.cos(angle) * radius))
            : previous.position();
        Vec offset = start.subtract(subject);
        Shot.Type type = Shot.Type.values()[random.nextInt(3)];
        if (type == last) type = Shot.Type.values()[(type.ordinal() + 1) % 3];
        if (fallback || motion == CinematicConfig.Motion.STILL) type = Shot.Type.STILL;
        double amount = motion == CinematicConfig.Motion.REDUCED ? 0.3 : 1.0;
        double sign = random.nextBoolean() ? 1 : -1;
        Vec end = start;
        Vec control = start;
        switch (type) {
            case ARC -> {
                double sweep = Math.toRadians((20 + random.nextDouble() * 15) * amount * sign);
                end = subject.add(rotate(offset, sweep));
                Vec midpoint = rotate(offset, sweep / 2);
                double scale = 1 / Math.cos(sweep / 2);
                control = subject.add(new Vec(midpoint.x() * scale, midpoint.y(), midpoint.z() * scale));
            }
            case DOLLY -> {
                double horizontal = Math.hypot(offset.x(), offset.z());
                double change = Math.clamp(horizontal + 0.7 * amount * sign, 1.5, 6.0);
                if (horizontal > 0.01)
                    end = subject.add(new Vec(offset.x() * change / horizontal, offset.y(), offset.z() * change / horizontal));
                control = start.lerp(end, 0.5);
            }
            case DRIFT -> {
                double horizontal = Math.hypot(offset.x(), offset.z());
                if (horizontal > 0.01)
                    end = start.add(new Vec(-offset.z(), 0, offset.x()).scale(amount * sign / horizontal));
                control = start.lerp(end, 0.5);
            }
            case STILL -> { }
        }
        Shot candidate = new Shot(type, subject, start, limitDistance(control), limitDistance(end), 15 + random.nextDouble() * 10);
        // A first-person fallback would rotate the view into the player's own body.
        if (Math.hypot(offset.x(), offset.z()) < 1.25) return null;
        return geometry.valid(candidate, previous.position()) ? candidate : null;
    }

    private static Vec rotate(Vec vector, double angle) {
        double sin = Math.sin(angle), cos = Math.cos(angle);
        return new Vec(vector.x() * cos - vector.z() * sin, vector.y(), vector.x() * sin + vector.z() * cos);
    }

    private Vec limitDistance(Vec point) {
        Vec offset = point.subtract(subject);
        double radius = Math.hypot(offset.x(), offset.z());
        if (radius <= 6) return point;
        return subject.add(new Vec(offset.x() * 6 / radius, offset.y(), offset.z() * 6 / radius));
    }
}
