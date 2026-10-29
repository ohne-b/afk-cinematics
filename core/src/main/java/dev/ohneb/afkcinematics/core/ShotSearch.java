package dev.ohneb.afkcinematics.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** One candidate per call so world queries can be spread across client ticks. */
public final class ShotSearch {
    private static final double[] ANGLES = {0, 30, -30, 60, -60, 90, -90, 135, -135, 180};
    private final Vec subject;
    private final Pose previous;
    private final double bearing;
    private final double distance;
    private final CinematicConfig.Motion motion;
    private final CinematicConfig.Pace pace;
    private final List<Shot.Type> types;
    private final boolean entering;
    private final Random random;
    private int attempt;

    public ShotSearch(Vec subject, Pose previous, double bearing, CinematicConfig config,
                      Shot.Type last, boolean entering, Random random) {
        this.subject = subject;
        this.previous = previous;
        this.bearing = bearing;
        this.distance = config.distance.blocks;
        this.motion = config.motion;
        this.pace = config.pace;
        this.entering = entering;
        this.random = random;
        types = new ArrayList<>(config.shots);
        if (motion == CinematicConfig.Motion.STILL || types.isEmpty()) {
            types.clear();
            types.add(Shot.Type.STILL);
        }
        Collections.shuffle(types, random);
        if (types.size() > 1 && types.remove(last)) types.add(last);
    }

    public boolean exhausted() { return attempt >= ANGLES.length * types.size(); }

    public Shot next(Geometry geometry) {
        if (exhausted()) return null;
        int index = attempt++;
        Shot.Type type = types.get(index % types.size());
        int view = index / types.size();
        double angle = Math.toRadians(bearing + ANGLES[view]);
        double radius = view < 5 ? distance : Math.min(distance, 2.0);
        Vec start = entering
            ? subject.add(new Vec(Math.sin(angle) * radius, 0.55, -Math.cos(angle) * radius))
            : previous.position();
        if (entering && type == Shot.Type.OVERHEAD)
            start = subject.add(new Vec(Math.sin(angle) * 1.7, 4.2, -Math.cos(angle) * 1.7));
        Vec offset = start.subtract(subject);
        double horizontal = Math.hypot(offset.x(), offset.z());
        // Looking back from inside the player's body is never a useful fallback.
        if (horizontal < 1.25) return null;
        double amount = motion == CinematicConfig.Motion.REDUCED ? 0.3 : 1.0;
        double sign = random.nextBoolean() ? 1 : -1;
        Vec tangent = new Vec(-offset.z(), 0, offset.x()).scale(1 / horizontal);
        Vec end = start;
        Vec control = start;
        Vec focusStart = entering ? subject : previous.lookAhead(start.distance(subject));
        Vec focusEnd = subject;
        switch (type) {
            case ARC -> {
                double sweep = Math.toRadians((20 + random.nextDouble() * 15) * amount * sign);
                end = subject.add(rotate(offset, sweep));
                Vec midpoint = rotate(offset, sweep / 2);
                double scale = 1 / Math.cos(sweep / 2);
                control = subject.add(new Vec(midpoint.x() * scale, midpoint.y(), midpoint.z() * scale));
            }
            case DOLLY -> {
                double change = Math.clamp(horizontal + 0.7 * amount * sign, 1.5, 6.0);
                end = subject.add(new Vec(offset.x() * change / horizontal, offset.y(), offset.z() * change / horizontal));
                control = start.lerp(end, 0.5);
            }
            case DRIFT -> {
                end = start.add(tangent.scale(amount * sign));
                control = start.lerp(end, 0.5);
            }
            case CRANE -> {
                double lift = (offset.y() > 2.5 ? -1.2 : 1.2) * amount;
                end = start.add(tangent.scale(0.4 * amount * sign)).add(new Vec(0, lift, 0));
                control = start.lerp(end, 0.5).add(new Vec(0, lift * 0.2, 0));
            }
            case OVERHEAD -> {
                Vec above = new Vec(offset.x() * 1.7 / horizontal, 4.8, offset.z() * 1.7 / horizontal);
                end = start.lerp(subject.add(rotate(above, Math.toRadians(15 * sign))), amount);
                control = start.lerp(end, 0.5);
            }
            case PAN -> {
                if (entering) focusStart = subject.add(tangent.scale(-0.9 * amount * sign));
                focusEnd = subject.add(tangent.scale(0.9 * amount * sign));
            }
            case STILL -> focusEnd = focusStart;
        }
        double duration = pace.minimumSeconds + random.nextDouble() * (pace.maximumSeconds - pace.minimumSeconds);
        Shot candidate = new Shot(type, start, limitDistance(control), limitDistance(end), focusStart, focusEnd, duration);
        return geometry.valid(candidate, previous.position()) ? candidate : null;
    }

    private static Vec rotate(Vec vector, double angle) {
        double sin = Math.sin(angle), cos = Math.cos(angle);
        return new Vec(vector.x() * cos - vector.z() * sin, vector.y(), vector.x() * sin + vector.z() * cos);
    }

    private Vec limitDistance(Vec point) {
        Vec offset = point.subtract(subject);
        double radius = Math.hypot(offset.x(), offset.z());
        double scale = radius > 6 ? 6 / radius : 1;
        return subject.add(new Vec(offset.x() * scale, Math.clamp(offset.y(), -0.3, 5.5), offset.z() * scale));
    }
}
