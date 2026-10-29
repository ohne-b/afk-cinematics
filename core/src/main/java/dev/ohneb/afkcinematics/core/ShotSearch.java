package dev.ohneb.afkcinematics.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** One candidate per call so world queries can be spread across client ticks. */
public final class ShotSearch {
    private static final double[] ANGLES = {0, 30, -30, 60, -60, 90, -90, 135, -135, 180};
    private static final List<Double> PLAYER_ANGLES = List.of(25.0, -25.0, 65.0, -65.0, 115.0, -115.0, 155.0, -155.0);
    private static final List<Double> PLAYER_DISTANCES = List.of(7.0, 10.0, 14.0);
    private final Vec subject;
    private final Scene scene;
    private final Pose previous;
    private final double bearing;
    private final CinematicConfig.Motion motion;
    private final CinematicConfig.Pace pace;
    private final List<Shot.Type> types;
    private final List<Double> angles;
    private final List<Double> distances;
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
        this.motion = config.motion;
        this.pace = config.pace;
        this.entering = entering;
        this.cut = cut;
        this.random = random;
        angles = new ArrayList<>();
        distances = new ArrayList<>();
        if (scene.scenery()) {
            for (double angle : ANGLES) angles.add(angle);
            distances.add(0.0);
        } else {
            angles.addAll(PLAYER_ANGLES);
            Collections.shuffle(angles, random);
            distances.addAll(PLAYER_DISTANCES);
            Collections.shuffle(distances, random);
            distances.add(5.5);
        }
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
                || type == Shot.Type.REVEAL || type == Shot.Type.CRANE
                || type == Shot.Type.RISING_PASS || type == Shot.Type.DESCENDING_PASS);
        if (scene.kind() == Scene.Kind.AERIAL)
            styles.removeIf(type -> type == Shot.Type.LOW_PASS || type == Shot.Type.REVEAL || type == Shot.Type.OVERHEAD);
        if (!scene.scenery()) styles.removeIf(Shot.Type::sceneryOnly);
        return styles;
    }

    public boolean exhausted() { return attempt >= angles.size() * types.size() * distances.size(); }

    void prioritize(ShotHistory history) {
        types.sort(Comparator.comparingInt(history::lastUse));
        if (!scene.scenery()) {
            distances.subList(0, PLAYER_DISTANCES.size()).sort(Comparator.comparingDouble(radius -> angles.stream().mapToDouble(angle ->
                history.viewPenalty(playerView(angle, radius), false)).average().orElse(0)));
            angles.sort(Comparator.comparingDouble(angle -> history.viewPenalty(playerView(angle, distances.getFirst()), false)));
        }
    }

    private Pose playerView(double angle, double radius) {
        double radians = Math.toRadians(bearing + angle);
        return Pose.lookingAt(subject.add(new Vec(Math.sin(radians) * radius, 0.55, -Math.cos(radians) * radius)), subject);
    }

    static int playerAttempts(CinematicConfig config) {
        int styles = config.motion == CinematicConfig.Motion.STILL || config.shots.isEmpty() ? 1
            : (int)config.shots.stream().filter(type -> !type.sceneryOnly()).count();
        return PLAYER_ANGLES.size() * (PLAYER_DISTANCES.size() + 1) * styles;
    }

    public Shot next(Geometry geometry) {
        if (exhausted()) return null;
        int index = attempt++;
        Shot.Type type = types.get(index % types.size());
        int view = index / types.size();
        double angle = Math.toRadians(bearing + angles.get(view % angles.size()));
        double radius = distances.get(view / angles.size());
        Vec start = entering
            ? subject.add(new Vec(Math.sin(angle) * radius, 0.55, -Math.cos(angle) * radius))
            : previous.position();
        if (entering && scene.scenery())
            start = limitDistance(subject.add(rotate(scene.vantage().subtract(subject), Math.toRadians(angles.get(view)))));
        if (entering && type == Shot.Type.PULL_AWAY) {
            Vec offset = start.subtract(subject);
            start = subject.add(new Vec(offset.x() * 0.65, offset.y(), offset.z() * 0.65));
        }
        if (entering && type == Shot.Type.DESCENDING_PASS && scene.kind() != Scene.Kind.WATER)
            start = new Vec(start.x(), Math.max(start.y(), subject.y() + 6), start.z());
        if (entering && type == Shot.Type.LOW_PASS)
            start = new Vec(start.x(), subject.y() + (scene.scenery() ? 0.25 : -0.6), start.z());
        if (entering && type == Shot.Type.FLYOVER && scene.kind() != Scene.Kind.WATER)
            start = new Vec(start.x(), Math.max(start.y(), subject.y() + (scene.scenery() ? 6 : 4)), start.z());
        if (entering && type == Shot.Type.REVEAL) {
            Vec offset = start.subtract(subject);
            double scale = Math.min(1, 7 / Math.hypot(offset.x(), offset.z()));
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
                double travel = (scene.scenery() ? 4 : 3.5) * amount;
                double minimum = scene.scenery() ? 1.5 : 5.5;
                double maximum = scene.kind() == Scene.Kind.AERIAL ? 24 : scene.scenery() ? 12 : 14;
                if (horizontal + travel * sign < minimum || horizontal + travel * sign > maximum) sign = -sign;
                double change = Math.clamp(horizontal + travel * sign, minimum, maximum);
                end = subject.add(new Vec(offset.x() * change / horizontal, offset.y(), offset.z() * change / horizontal));
                control = start.lerp(end, 0.5);
            }
            case DRIFT -> {
                end = limitDistance(start.add(tangent.scale((scene.scenery() ? 6 : 4.5) * amount * sign)));
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
                double high = scene.kind() == Scene.Kind.WATER ? 2.2 : 8;
                double wide = 3;
                Vec above = new Vec(offset.x() * wide / horizontal, high, offset.z() * wide / horizontal);
                end = start.lerp(subject.add(rotate(above, Math.toRadians(15 * sign))), amount);
                control = start.lerp(end, 0.5);
            }
            case FLYOVER -> {
                double travel = Math.min(scene.kind() == Scene.Kind.AERIAL ? 12 : scene.scenery() ? 7.5 : 4.5,
                    Math.max(0, horizontal - (scene.scenery() ? 1.25 : 5.5))) * amount;
                end = start.add(new Vec(-offset.x(), 0, -offset.z()).scale(travel / horizontal));
                control = start.lerp(end, 0.5);
                if (scene.scenery()) focusEnd = subject.add(end.subtract(start));
            }
            case FLY_THROUGH, TRAVERSE -> {
                double travel = (scene.kind() == Scene.Kind.AERIAL ? 14 : scene.kind() == Scene.Kind.WATER ? 7 : 10) * amount;
                Vec direction = type == Shot.Type.TRAVERSE ? tangent.scale(sign)
                    : new Vec(-offset.x(), 0, -offset.z()).scale(1 / horizontal);
                end = limitDistance(start.add(direction.scale(travel)));
                control = start.lerp(end, 0.5);
                // Move the composition with the camera: a drive through the scene, not an orbit.
                focusEnd = subject.add(end.subtract(start));
            }
            case DIAGONAL_GLIDE, RISING_PASS, DESCENDING_PASS, PULL_AWAY -> {
                double travel = (scene.kind() == Scene.Kind.AERIAL ? 12 : scene.kind() == Scene.Kind.WATER ? 5 : 8) * amount;
                Vec forward = new Vec(-offset.x(), 0, -offset.z()).scale(1 / horizontal);
                Vec direction = type == Shot.Type.PULL_AWAY ? forward.scale(-1) : forward;
                if (type == Shot.Type.DIAGONAL_GLIDE) {
                    direction = forward.add(tangent.scale(0.75 * sign));
                    direction = direction.scale(1 / direction.length());
                }
                double lift = type == Shot.Type.RISING_PASS ? 1 : type == Shot.Type.DESCENDING_PASS ? -1 : 0;
                lift *= (scene.kind() == Scene.Kind.WATER ? 1 : 4) * amount;
                end = limitDistance(start.add(direction.scale(travel)).add(new Vec(0, lift, 0)));
                control = start.lerp(end, 0.5);
                // Descend toward the surface without carrying the sightline below it.
                focusStart = subject.add(new Vec(0, Math.max(0, start.y() - end.y()), 0));
                focusEnd = focusStart.add(end.subtract(start));
            }
            case LOW_PASS -> {
                end = limitDistance(start.add(tangent.scale((scene.scenery() ? 6 : 4.5) * amount * sign)));
                control = start.lerp(end, 0.5);
                if (scene.scenery()) focusEnd = subject.add(end.subtract(start).scale(0.5));
            }
            case REVEAL -> {
                Vec retreat = new Vec(offset.x(), 0, offset.z()).scale(4 * amount / horizontal);
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
        boolean aerial = scene.kind() == Scene.Kind.AERIAL;
        double maximum = aerial ? 24 : scene.scenery() ? 12 : 14;
        double scale = radius > maximum ? maximum / radius : 1;
        return subject.add(new Vec(offset.x() * scale,
            Math.clamp(offset.y(), scene.scenery() ? -4 : -0.65, aerial ? 22 : scene.scenery() ? 12 : 5.5), offset.z() * scale));
    }
}
