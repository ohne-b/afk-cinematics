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

    ShotPlanner(Vec anchor, Pose previous, Pose original, double bearing, CinematicConfig config,
                ShotHistory history, Random random) {
        this(anchor, previous, original, bearing, config, history, random, Scene.Kind.PLAYER);
    }

    ShotPlanner(Vec anchor, Pose previous, Pose original, double bearing, CinematicConfig config,
                ShotHistory history, Random random, Scene.Kind activity) {
        this.anchor = anchor;
        this.previous = previous;
        this.original = original;
        this.bearing = bearing;
        this.sceneryBearing = bearing + random.nextDouble() * 360;
        this.config = config;
        this.history = history;
        this.random = random;
        travel = activity == Scene.Kind.BOAT || activity == Scene.Kind.FLIGHT
            ? new TravelSearch(anchor, activity, bearing, config, history, random) : null;
        triedPlayer = config.subject == CinematicConfig.Subject.SCENERY;
        triedScenery = config.subject == CinematicConfig.Subject.PLAYER;
        if (triedPlayer || triedScenery) sceneryFirst = triedPlayer;
        else sceneryFirst = history.sceneryFirst(config.playerShare, random);
    }

    boolean exhausted() { return travel != null ? travel.exhausted() : search == null && triedPlayer && triedScenery; }

    static double preparationSeconds(CinematicConfig config) {
        int player = config.subject == CinematicConfig.Subject.SCENERY ? 0 : ShotSearch.playerAttempts(config);
        int scenery = config.subject == CinematicConfig.Subject.PLAYER ? 0
            : Scene.PROBES + SCENERY_CANDIDATES * (SCENERY_ATTEMPTS + 1);
        // Two planning steps per tick, including a fallback after a rejected subject.
        return Math.max(4, (player + scenery + 2) / 40.0 + 0.5);
    }

    Plan next(Geometry geometry) {
        if (travel != null) return travel.next(geometry);
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
