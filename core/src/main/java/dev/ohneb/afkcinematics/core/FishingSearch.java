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
    private int attempt;

    FishingSearch(Vec subject, Vec bobber, boolean biting, CinematicConfig config, ShotHistory history, Random random) {
        this.subject = subject;
        this.bobber = bobber;
        this.config = config;
        this.random = random;
        types = new ArrayList<>(config.shots);
        types.removeIf(type -> !type.fishingOnly());
        if (types.isEmpty() && config.shots.contains(Shot.Type.STILL)) types.add(Shot.Type.STILL);
        Collections.shuffle(types, random);
        types.sort(Comparator.comparingInt(type -> biting && type == Shot.Type.FLOAT ? Integer.MIN_VALUE : history.lastUse(type)));
    }

    boolean exhausted() { return bobber == null || attempt >= types.size() * 6; }

    ShotPlanner.Plan next(Geometry geometry) {
        if (exhausted()) return null;
        int index = attempt++;
        Shot.Type type = types.get(index % types.size());
        int view = index / types.size();
        double side = view % 2 == 0 ? 1 : -1;
        double scale = 1 - view / 2 * .25;
        Vec line = bobber.subtract(subject);
        double length = Math.hypot(line.x(), line.z());
        if (length < .5 || length > 24) return null;
        Vec forward = new Vec(line.x() / length, 0, line.z() / length);
        Vec lateral = new Vec(-forward.z(), 0, forward.x()).scale(side);
        Vec focus = subject.lerp(bobber, weight(type));
        Vec start = switch (type) {
            case FLOAT -> bobber.subtract(forward.scale(3.5 * scale)).add(lateral.scale(2 * scale)).add(new Vec(0, 1.5 * scale, 0));
            case CAST_LINE -> subject.add(lateral.scale(3.5 * scale)).subtract(forward.scale(3 * scale)).add(new Vec(0, 1.8 * scale, 0));
            default -> focus.add(lateral.scale(8 * scale)).subtract(forward.scale(3 * scale)).add(new Vec(0, 3 * scale, 0));
        };
        if (!Scene.inRange(start, subject)) return null;
        double duration = config.pace.minimumSeconds + random.nextDouble() * (config.pace.maximumSeconds - config.pace.minimumSeconds);
        Shot shot = new Shot(type, start, start, start, focus, focus, duration);
        Scene scene = new Scene(bobber, start, Scene.Kind.FISHING, 0);
        Geometry environment = geometry.forScene(scene);
        return environment.valid(shot, start) && environment.visible(start, bobber)
            ? new ShotPlanner.Plan(shot, scene, true, true) : null;
    }

    static double weight(Shot.Type type) { return type == Shot.Type.FLOAT ? 1 : type == Shot.Type.CAST_LINE ? .65 : .4; }
}
