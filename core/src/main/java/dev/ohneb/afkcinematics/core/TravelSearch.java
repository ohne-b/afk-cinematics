package dev.ohneb.afkcinematics.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Course-relative compositions let the journey provide the camera motion. */
final class TravelSearch {
    private final Vec subject;
    private final Scene.Kind kind;
    private final CinematicConfig config;
    private final Random random;
    private final List<Shot.Type> types;
    private final int firstSide;
    private int attempt;

    TravelSearch(Vec subject, Scene.Kind kind, CinematicConfig config, ShotHistory history, Random random) {
        this.subject = subject;
        this.kind = kind;
        this.config = config;
        this.random = random;
        firstSide = random.nextBoolean() ? 1 : -1;
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
        double side = (view % 2 == 0 ? 1 : -1) * firstSide;
        double scale = switch (view / 2) { case 0 -> 1; case 1 -> 0.75; default -> 0.5; };
        double distance = switch (type) {
            case LOW_ESCORT -> flying ? 12 : 8;
            case HIGH_TRAIL -> 20;
            case BROADSIDE -> flying ? 20 : 14;
            case WIDE_TRACK -> flying ? 23 : 19;
            default -> flying ? 15 : 10;
        } * scale;
        double height = switch (type) {
            case LOW_ESCORT -> flying ? -.4 : .5;
            case HIGH_TRAIL -> flying ? 11 : 8;
            case BROADSIDE -> flying ? 2 : 1;
            case WIDE_TRACK -> flying ? 11 : 7;
            default -> flying ? 3 : 1.5;
        } * scale;
        double angle = switch (type) {
            case TRAIL -> 20 * side;
            case HIGH_TRAIL -> 15 * side;
            case LOW_ESCORT -> 65 * side;
            case BROADSIDE -> 90 * side;
            case ESCORT -> 75 * side;
            case LEAD -> 155 * side;
            default -> 55 * side;
        };
        if (kind == Scene.Kind.MINECART && type == Shot.Type.TRAIL && view >= 4) {
            // Keep the closest fallback inside a straight rail tunnel.
            angle = 0;
            height = 0;
        }
        double radians = Math.toRadians(angle);
        Vec start = subject.add(new Vec(Math.sin(radians) * distance, height, -Math.cos(radians) * distance));
        double lead = switch (type) {
            case HIGH_TRAIL, WIDE_TRACK -> 4;
            case ESCORT, BROADSIDE -> 3.5;
            case LEAD -> 1.5;
            default -> 2.5;
        };
        Vec focus = subject.add(new Vec(0, .2, lead * scale));
        double duration = config.pace.minimumSeconds + random.nextDouble() * (config.pace.maximumSeconds - config.pace.minimumSeconds);
        Shot shot = new Shot(type, start, start, start, focus, focus, duration);
        Scene scene = new Scene(subject, start, kind, 0);
        Geometry environment = geometry.forScene(scene);
        return environment.valid(shot, start) && environment.visible(start, subject)
            ? new ShotPlanner.Plan(shot, scene, true, true) : null;
    }
}
