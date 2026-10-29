package dev.ohneb.afkcinematics.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** One candidate per call so world queries can be spread across client ticks. */
public final class ShotSearch {
    private static final double[] ANGLES = {0, 30, -30, 60, -60, 90, -90, 135, -135, 180};
    private final Vec subject;
    private final Scene scene;
    private final Pose previous;
    private final double bearing;
    private final double distance;
    private final CinematicConfig.Motion motion;
    private final CinematicConfig.Pace pace;
    private final List<Shot.Type> types;
    private final List<Double> angles;
    private final boolean entering;
    private final boolean cut;
    private final Random random;
    private int attempt;

    public ShotSearch(Scene scene, Pose previous, double bearing, CinematicConfig config,
                      Shot.Type last, boolean entering, boolean cut, Random random) {
        this.scene = scene;
        this.subject = scene.focus();
        this.previous = previous;
        this.bearing = bearing;
        this.distance = config.distance.blocks;
        this.motion = config.motion;
        this.pace = config.pace;
        this.entering = entering;
        this.cut = cut;
        this.random = random;
        angles = new ArrayList<>();
        if (!scene.scenery()) {
            angles.addAll(List.of(0.0, 180.0, 90.0, -90.0));
            Collections.shuffle(angles, random);
        }
        for (double angle : ANGLES) if (!angles.contains(angle)) angles.add(angle);
        types = stylesFor(scene, config);
        Collections.shuffle(types, random);
        if (types.size() > 1 && types.remove(last)) types.add(last);
    }

    static List<Shot.Type> stylesFor(Scene scene, CinematicConfig config) {
        var styles = new ArrayList<>(config.shots);
        if (config.motion == CinematicConfig.Motion.STILL || styles.isEmpty()) {
            styles.clear();
            styles.add(Shot.Type.STILL);
        }
        if (scene.kind() == Scene.Kind.GROUND)
            styles.removeIf(type -> type == Shot.Type.FLYOVER || type == Shot.Type.OVERHEAD
                || type == Shot.Type.REVEAL || type == Shot.Type.CRANE);
        return styles;
    }

    public boolean exhausted() { return attempt >= angles.size() * types.size() * (scene.scenery() ? 1 : 2); }

    static int playerAttempts(CinematicConfig config) {
        int styles = config.motion == CinematicConfig.Motion.STILL || config.shots.isEmpty() ? 1 : config.shots.size();
        return ANGLES.length * 2 * styles;
    }

    public Shot next(Geometry geometry) {
        if (exhausted()) return null;
        int index = attempt++;
        Shot.Type type = types.get(index % types.size());
        int view = index / types.size();
        double angle = Math.toRadians(bearing + angles.get(view % angles.size()));
        double radius = view < angles.size() ? distance : Math.min(distance, 2.0);
        Vec start = entering
            ? subject.add(new Vec(Math.sin(angle) * radius, 0.55, -Math.cos(angle) * radius))
            : previous.position();
        if (entering && scene.scenery())
            start = limitDistance(subject.add(rotate(scene.vantage().subtract(subject), Math.toRadians(angles.get(view)))));
        if (entering && type == Shot.Type.OVERHEAD && !scene.scenery())
            start = subject.add(new Vec(Math.sin(angle) * 1.7, 4.2, -Math.cos(angle) * 1.7));
        if (entering && type == Shot.Type.LOW_PASS)
            start = new Vec(start.x(), subject.y() + (scene.scenery() ? 0.25 : -0.6), start.z());
        if (entering && type == Shot.Type.FLYOVER && scene.kind() != Scene.Kind.WATER)
            start = new Vec(start.x(), Math.max(start.y(), subject.y() + (scene.scenery() ? 6 : 4)), start.z());
        if (entering && type == Shot.Type.REVEAL) {
            Vec offset = start.subtract(subject);
            double scale = Math.min(1, (scene.scenery() ? 7 : 3) / Math.hypot(offset.x(), offset.z()));
            start = subject.add(new Vec(offset.x() * scale, Math.min(offset.y(), 1.2), offset.z() * scale));
        }
        Vec offset = start.subtract(subject);
        double horizontal = Math.hypot(offset.x(), offset.z());
        // Looking back from inside the player's body is never a useful fallback.
        if (horizontal < (scene.scenery() ? 0.3 : 1.25)) return null;
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
                double travel = (scene.scenery() ? 4 : 2) * amount;
                double maximum = scene.scenery() ? 12 : 6;
                if (horizontal + travel * sign < 1.5 || horizontal + travel * sign > maximum) sign = -sign;
                double change = Math.clamp(horizontal + travel * sign, 1.5, maximum);
                end = subject.add(new Vec(offset.x() * change / horizontal, offset.y(), offset.z() * change / horizontal));
                control = start.lerp(end, 0.5);
            }
            case DRIFT -> {
                end = limitDistance(start.add(tangent.scale((scene.scenery() ? 6 : 3) * amount * sign)));
                control = start.lerp(end, 0.5);
                if (scene.scenery()) focusEnd = subject.add(end.subtract(start).scale(0.35));
            }
            case CRANE -> {
                double travel = (scene.kind() == Scene.Kind.WATER ? 1.2 : scene.scenery() ? 3.5 : 2) * amount;
                double lift = (offset.y() > travel + 0.5 ? -1 : 1) * travel;
                end = start.add(tangent.scale(0.8 * amount * sign)).add(new Vec(0, lift, 0));
                control = start.lerp(end, 0.5).add(new Vec(0, lift * 0.2, 0));
            }
            case OVERHEAD -> {
                double high = scene.kind() == Scene.Kind.WATER ? 2.2 : scene.scenery() ? 8 : 4.8;
                double wide = scene.scenery() ? 3 : 1.7;
                Vec above = new Vec(offset.x() * wide / horizontal, high, offset.z() * wide / horizontal);
                end = start.lerp(subject.add(rotate(above, Math.toRadians(15 * sign))), amount);
                control = start.lerp(end, 0.5);
            }
            case FLYOVER -> {
                double travel = Math.min(scene.scenery() ? 7.5 : 2.5, Math.max(0, horizontal - 1.25)) * amount;
                end = start.add(new Vec(-offset.x(), 0, -offset.z()).scale(travel / horizontal));
                control = start.lerp(end, 0.5);
                if (scene.scenery()) focusEnd = subject.add(end.subtract(start));
            }
            case LOW_PASS -> {
                end = limitDistance(start.add(tangent.scale((scene.scenery() ? 6 : 3) * amount * sign)));
                control = start.lerp(end, 0.5);
                if (scene.scenery()) focusEnd = subject.add(end.subtract(start).scale(0.5));
            }
            case REVEAL -> {
                Vec retreat = new Vec(offset.x(), 0, offset.z()).scale((scene.scenery() ? 4 : 2.5) * amount / horizontal);
                double lift = scene.kind() == Scene.Kind.WATER ? 1 : scene.scenery() ? 3.5 : 2.5;
                end = limitDistance(start.add(retreat).add(new Vec(0, lift * amount, 0)));
                control = start.lerp(end, 0.5);
            }
            case PAN -> {
                if (entering) focusStart = subject.add(tangent.scale(-0.9 * amount * sign));
                else {
                    Vec initial = focusStart.subtract(subject);
                    if ((initial.x() * tangent.x() + initial.z() * tangent.z()) * sign > 0) sign = -sign;
                }
                focusEnd = subject.add(tangent.scale(0.9 * amount * sign));
            }
            case STILL -> focusEnd = focusStart;
        }
        double duration = pace.minimumSeconds + random.nextDouble() * (pace.maximumSeconds - pace.minimumSeconds);
        Shot candidate = new Shot(type, start, limitDistance(control), limitDistance(end), focusStart, focusEnd, duration);
        if (type != Shot.Type.STILL && type != Shot.Type.PAN && candidate.start().distance(candidate.end()) < 0.01) return null;
        return geometry.valid(candidate, cut ? candidate.start() : previous.position()) ? candidate : null;
    }

    private static Vec rotate(Vec vector, double angle) {
        double sin = Math.sin(angle), cos = Math.cos(angle);
        return new Vec(vector.x() * cos - vector.z() * sin, vector.y(), vector.x() * sin + vector.z() * cos);
    }

    private Vec limitDistance(Vec point) {
        Vec offset = point.subtract(subject);
        double radius = Math.hypot(offset.x(), offset.z());
        double maximum = scene.scenery() ? 12 : 6;
        double scale = radius > maximum ? maximum / radius : 1;
        return subject.add(new Vec(offset.x() * scale,
            Math.clamp(offset.y(), scene.scenery() ? -4 : -0.65, scene.scenery() ? 12 : 5.5), offset.z() * scale));
    }
}
