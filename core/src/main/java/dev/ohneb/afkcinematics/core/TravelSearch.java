package dev.ohneb.afkcinematics.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Stable offsets let the journey provide the camera motion. */
final class TravelSearch {
    private final Vec subject;
    private final Scene.Kind kind;
    private final CinematicConfig config;
    private final Random random;
    private final List<Shot.Type> types;
    private int attempt;

    TravelSearch(Vec subject, Scene.Kind kind, CinematicConfig config, ShotHistory history, Random random) {
        this.subject = subject;
        this.kind = kind;
        this.config = config;
        this.random = random;
        types = new ArrayList<>(config.shots);
        types.removeIf(type -> !type.travelOnly());
        if (types.isEmpty() && config.shots.contains(Shot.Type.STILL)) types.add(Shot.Type.STILL);
        Collections.shuffle(types, random);
        types.sort(Comparator.comparingInt(history::lastUse));
    }

    boolean exhausted() { return attempt >= types.size() * 6; }

    ShotPlanner.Plan next(Geometry geometry) {
        if (exhausted()) return null;
        int index = attempt++;
        Shot.Type type = types.get(index % types.size());
        int view = index / types.size();
        boolean flying = kind == Scene.Kind.FLIGHT;
        double side = view % 2 == 0 ? 1 : -1;
        double scale = switch (view / 2) { case 0 -> 1; case 1 -> 0.75; default -> 0.5; };
        double distance = (type == Shot.Type.WIDE_TRACK ? (flying ? 23 : 19) : flying ? 15 : 10) * scale;
        double height = (type == Shot.Type.WIDE_TRACK ? (flying ? 11 : 7) : flying ? 3 : 1.5) * scale;
        double angle = switch (type) {
            case TRAIL -> 20 * side;
            case ESCORT -> 75 * side;
            case LEAD -> 155 * side;
            default -> 55 * side;
        };
        double radians = Math.toRadians(angle);
        Vec start = subject.add(new Vec(Math.sin(radians) * distance, height, -Math.cos(radians) * distance));
        double duration = config.pace.minimumSeconds + random.nextDouble() * (config.pace.maximumSeconds - config.pace.minimumSeconds);
        Shot shot = new Shot(type, start, start, start, subject, subject, duration);
        Scene scene = new Scene(subject, start, kind, 0);
        return geometry.forScene(scene).valid(shot, start) ? new ShotPlanner.Plan(shot, scene, true, true) : null;
    }
}
