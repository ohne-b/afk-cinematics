package dev.ohneb.afkcinematics.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Each step performs one terrain probe or validates one shot. */
final class ShotPlanner {
    private static final int SCENERY_CANDIDATES = 4;
    private static final int SCENERY_ATTEMPTS = 25;
    record Plan(Shot shot, Scene scene, boolean cut, boolean entering) { }
    private final Vec anchor;
    private final Pose previous;
    private final Pose original;
    private final double bearing;
    private final double sceneryBearing;
    private final CinematicConfig config;
    private final ShotHistory history;
    private final Random random;
    private final boolean sceneryFirst;
    private final List<Scene> candidates = new ArrayList<>();
    private boolean triedPlayer, triedScenery;
    private int probe, candidate, attempts;
    private Scene selected;
    private ShotSearch search;
    private boolean cut, entering;
    private final TravelSearch travel;
    private final FishingSearch fishing;
    private final boolean tryWildlife;
    private int animalProbe;

    ShotPlanner(Vec anchor, Pose previous, Pose original, double bearing, CinematicConfig config,
                ShotHistory history, Random random) {
        this(anchor, previous, original, bearing, config, history, random, Scene.Kind.PLAYER, null);
    }

    ShotPlanner(Vec anchor, Pose previous, Pose original, double bearing, CinematicConfig config,
                ShotHistory history, Random random, Scene.Kind activity, Vec bobber) {
        this.anchor = anchor;
        this.previous = previous;
        this.original = original;
        this.bearing = bearing;
        this.sceneryBearing = bearing + random.nextDouble() * 360;
        this.config = config;
        this.history = history;
        this.random = random;
        travel = activity.travel() || activity == Scene.Kind.FISHING
            ? new TravelSearch(anchor, activity == Scene.Kind.FISHING ? Scene.Kind.FOLLOW : activity, config, history, random) : null;
        fishing = activity == Scene.Kind.FISHING ? new FishingSearch(anchor, bobber, config, history, random) : null;
        triedPlayer = config.subject == CinematicConfig.Subject.SCENERY;
        triedScenery = config.subject == CinematicConfig.Subject.PLAYER;
        if (triedPlayer || triedScenery) sceneryFirst = triedPlayer;
        else sceneryFirst = history.sceneryFirst(config.playerShare, random);
        tryWildlife = activity == Scene.Kind.PLAYER && config.wildlife && !triedScenery
            && (config.subject == CinematicConfig.Subject.SCENERY || config.playerShare < 100)
            && history.wildlifeReady() && random.nextDouble() < .15
            && (config.shots.contains(Shot.Type.DRIFT) || config.shots.contains(Shot.Type.STILL));
    }

    boolean exhausted() { return travel != null ? (fishing == null || fishing.exhausted()) && travel.exhausted() : search == null && triedPlayer && triedScenery; }

    static double preparationSeconds(CinematicConfig config) {
        int player = config.subject == CinematicConfig.Subject.SCENERY ? 0 : ShotSearch.playerAttempts(config);
        int scenery = config.subject == CinematicConfig.Subject.PLAYER ? 0
            : Scene.PROBES + SCENERY_CANDIDATES * (SCENERY_ATTEMPTS + 1);
        // Two planning steps per tick, including a fallback after a rejected subject.
        return Math.max(4, (player + scenery + 10) / 40.0 + 0.5);
    }

    Plan next(Geometry geometry) {
        if (fishing != null && !fishing.exhausted()) return fishing.next(geometry);
        if (travel != null) return travel.next(geometry);
        if (tryWildlife && animalProbe < 8) {
            Scene animal = geometry.wildlife(anchor, sceneryBearing, history.lastSpecies(), animalProbe++);
            if (animal == null || !animal.near(anchor) || !geometry.available(animal)) return null;
            Vec start = animal.vantage();
            Vec offset = start.subtract(animal.focus());
            boolean still = config.motion == CinematicConfig.Motion.STILL || !config.shots.contains(Shot.Type.DRIFT);
            double drift = config.motion == CinematicConfig.Motion.REDUCED ? .25 : .8;
            Vec end = still ? start : start.add(new Vec(-offset.z(), 0, offset.x()).scale(drift / Math.max(1, offset.length())));
            Shot shot = new Shot(still ? Shot.Type.STILL : Shot.Type.DRIFT, start, start.lerp(end, .5), end,
                animal.focus(), animal.focus(), 10);
            return Scene.inRange(end, anchor) && geometry.forScene(animal).valid(shot, start)
                ? new Plan(shot, animal, true, true) : null;
        }
        if (search == null) {
            if (!triedPlayer && (!sceneryFirst || triedScenery)) {
                triedPlayer = true;
                selected = new Scene(anchor, original.position(), Scene.Kind.PLAYER, 0);
                cut = !history.empty();
                entering = true;
            } else if (!triedScenery) {
                if (probe < Scene.PROBES) {
                    Scene scene = geometry.scenery(anchor, original, sceneryBearing, probe++);
                    if (scene != null && scene.scenery() && scene.near(anchor) && !ShotSearch.stylesFor(scene, config).isEmpty()
                        && candidates.stream().noneMatch(other -> other.focus().distance(scene.focus()) < 3
                            && other.vantage().distance(scene.vantage()) < 3)) candidates.add(scene);
                    if (probe == Scene.PROBES) candidates.sort(Comparator.comparingDouble(history::sceneScore).reversed());
                    return null;
                }
                if (candidate >= Math.min(SCENERY_CANDIDATES, candidates.size())) { triedScenery = true; return null; }
                selected = candidates.get(candidate++);
                cut = true;
                entering = true;
            } else return null;
            search = new ShotSearch(selected, previous, bearing, config, history.lastType(), entering, cut, random);
            search.prioritize(history);
            attempts = 0;
        }
        Shot shot = search.next(geometry.forScene(selected));
        if (shot != null && Scene.inRange(shot.start(), anchor) && Scene.inRange(shot.control(), anchor)
            && Scene.inRange(shot.end(), anchor)) return new Plan(shot, selected, cut, entering);
        if (search.exhausted() || selected.scenery() && ++attempts >= SCENERY_ATTEMPTS) search = null;
        return null;
    }

}
