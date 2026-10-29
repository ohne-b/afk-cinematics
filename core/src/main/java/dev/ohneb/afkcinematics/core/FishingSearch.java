package dev.ohneb.afkcinematics.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

final class FishingSearch {
    private final Vec subject, bobber;
    private final CinematicConfig config;
    private final Random random;
    private final List<Shot.Type> types;
    private final ShotHistory history;
    private final int firstSide;
    private ShotPlanner.Plan best;
    private double bestScore = Double.NEGATIVE_INFINITY;
    private int attempt;

    FishingSearch(Vec subject, Vec bobber, CinematicConfig config, ShotHistory history, Random random) {
        this.subject = subject;
        this.bobber = bobber;
        this.config = config;
        this.random = random;
        this.history = history;
        firstSide = random.nextBoolean() ? 1 : -1;
        types = new ArrayList<>(config.shots);
        types.removeIf(type -> !type.fishingOnly());
        if (types.isEmpty() && config.shots.contains(Shot.Type.STILL)) types.add(Shot.Type.STILL);
        Collections.shuffle(types, random);
        types.sort(Comparator.comparingInt(history::lastUse));
    }

    boolean exhausted() { return bobber == null || attempt >= types.size() * 6; }

    ShotPlanner.Plan next(Geometry geometry) {
        if (exhausted()) return null;
        int index = attempt++;
        Shot.Type type = types.get(index / 6);
        int view = index % 6;
        ShotPlanner.Plan candidate = candidate(type, view, geometry);
        if (candidate != null) {
            Pose middle = candidate.shot().sample(candidate.shot().duration() * .5);
            double score = geometry.viewQuality(middle) - history.viewPenalty(middle, false)
                + (1 - view / 2 * .2);
            if (score > bestScore) { best = candidate; bestScore = score; }
        }
        // Compare both sides and all clear distances before committing to a composition.
        return view == 5 ? best : null;
    }

    private ShotPlanner.Plan candidate(Shot.Type type, int view, Geometry geometry) {
        double side = (view % 2 == 0 ? 1 : -1) * firstSide;
        double scale = 1 - view / 2 * .2;
        Vec line = bobber.subtract(subject);
        double length = Math.hypot(line.x(), line.z());
        if (length < .5 || length > 24) return null;
        Vec forward = new Vec(line.x() / length, 0, line.z() / length);
        Vec lateral = new Vec(-forward.z(), 0, forward.x()).scale(side);
        Vec focus = subject.lerp(bobber, weight(type)).add(new Vec(0, type == Shot.Type.FLOAT ? .3 : .5, 0));
        Vec start = switch (type) {
            case FLOAT -> bobber.add(forward.scale((4 + length * .15) * scale))
                .add(lateral.scale((4 + length * .15) * scale)).add(new Vec(0, 1.2, 0));
            case CAST_LINE -> subject.add(lateral.scale(6 * scale)).subtract(forward.scale(7 * scale)).add(new Vec(0, 1, 0));
            default -> focus.add(lateral.scale(Math.max(10, length * .8 + 5) * scale))
                .subtract(forward.scale(3.5 * scale)).add(new Vec(0, 2.2 * scale, 0));
        };
        double amount = type == Shot.Type.STILL || config.motion == CinematicConfig.Motion.STILL ? 0 : config.motion == CinematicConfig.Motion.REDUCED ? .3 : 1;
        Vec drive = switch (type) {
            case FLOAT -> lateral.scale(-2.4).add(forward.scale(.4));
            case CAST_LINE -> lateral.scale(2.5).add(forward);
            default -> forward.scale(3).subtract(lateral);
        };
        Vec end = start.add(drive.scale(amount));
        if (!Scene.inRange(start, subject) || !Scene.inRange(end, subject)) return null;
        double duration = config.pace.minimumSeconds + random.nextDouble() * (config.pace.maximumSeconds - config.pace.minimumSeconds);
        Shot shot = new Shot(type, start, start.lerp(end, .5), end, focus, focus, duration);
        Scene scene = new Scene(bobber, start, Scene.Kind.FISHING, 0);
        Geometry environment = geometry.forScene(scene);
        Shot sightline = new Shot(type, start, shot.control(), end, bobber, bobber, duration);
        Shot angler = new Shot(type, start, shot.control(), end, subject, subject, duration);
        return environment.valid(shot, start) && environment.valid(sightline, start) && environment.valid(angler, start)
            ? new ShotPlanner.Plan(shot, scene, true, true) : null;
    }

    static double weight(Shot.Type type) { return type == Shot.Type.FLOAT ? .3 : type == Shot.Type.CAST_LINE ? .38 : .45; }
}
