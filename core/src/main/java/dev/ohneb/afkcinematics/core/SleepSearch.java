package dev.ohneb.afkcinematics.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Bed-relative compositions, with a bounded search for the brief opening take. */
final class SleepSearch {
    private final Vec subject, forward;
    private final CinematicConfig config;
    private final ShotHistory history;
    private final Random random;
    private final List<Shot.Type> types = new ArrayList<>();
    private final int side;
    private int attempt;

    SleepSearch(Vec subject, double bearing, CinematicConfig config, ShotHistory history, Random random) {
        this.subject = subject;
        forward = new Vec(-Math.sin(Math.toRadians(bearing)), 0, Math.cos(Math.toRadians(bearing)));
        this.config = config;
        this.history = history;
        this.random = random;
        side = random.nextBoolean() ? 1 : -1;
        for (Shot.Type type : List.of(Shot.Type.DOLLY, Shot.Type.DRIFT, Shot.Type.STILL))
            if (config.shots.contains(type)) types.add(type);
        if (config.motion == CinematicConfig.Motion.STILL) {
            types.clear();
            types.add(Shot.Type.STILL);
        }
        types.sort(Comparator.comparingInt(history::lastUse));
    }

    boolean exhausted() { return attempt >= types.size() * 6; }

    ShotPlanner.Plan next(Geometry geometry) {
        if (exhausted()) return null;
        int index = attempt++;
        Shot.Type type = types.get(index / 6);
        int view = index % 6;
        double distance = switch (view / 2) { case 0 -> 3.2; case 1 -> 5.5; default -> 2.4; };
        Vec lateral = new Vec(-forward.z(), 0, forward.x()).scale(side * (view % 2 == 0 ? 1 : -1));
        Vec start = subject.add(lateral.scale(distance)).subtract(forward.scale(distance * .35)).add(new Vec(0, .9, 0));
        double amount = type == Shot.Type.STILL ? 0 : config.motion == CinematicConfig.Motion.REDUCED ? .3 : 1;
        Vec drive = type == Shot.Type.DOLLY ? start.subtract(subject).scale(1.5) : forward.scale(4);
        Vec end = start.add(drive.scale(amount));
        double duration = history.empty() ? 8 : config.pace.minimumSeconds
            + random.nextDouble() * (config.pace.maximumSeconds - config.pace.minimumSeconds);
        Shot shot = new Shot(type, start, start.lerp(end, .5), end, subject, subject,
            CinematicConfig.Pace.LONG.maximumSeconds).cutAfter(duration);
        Scene scene = new Scene(subject, start, Scene.Kind.SLEEP, 0);
        Geometry environment = geometry.forScene(scene);
        if (!geometry.available(scene, start) || !geometry.available(scene, shot.end()) || !environment.valid(shot, start)) return null;
        for (int direction : new int[]{-1, 1}) {
            Vec body = subject.add(forward.scale(direction * .65));
            if (!environment.valid(new Shot(type, start, shot.control(), shot.end(), body, body, duration), start)) return null;
        }
        return new ShotPlanner.Plan(shot, scene);
    }
}
