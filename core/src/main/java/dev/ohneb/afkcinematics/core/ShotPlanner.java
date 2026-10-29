package dev.ohneb.afkcinematics.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Each step performs one terrain probe or validates one shot. */
final class ShotPlanner {
    private static final int SCENERY_CANDIDATES = 4;
    private static final int SCENERY_ATTEMPTS = 21;
    record Plan(Shot shot, Scene scene, boolean cut, boolean entering) { }
    private final Vec anchor;
    private final Pose previous;
    private final Pose original;
    private final double bearing;
    private final CinematicConfig config;
    private final Scene lastScene;
    private final List<Pose> recentViews;
    private final Shot.Type lastType;
    private final Random random;
    private final boolean sceneryFirst;
    private final List<Scene> candidates = new ArrayList<>();
    private boolean triedPlayer, triedScenery;
    private int probe, candidate, attempts;
    private Scene selected;
    private ShotSearch search;
    private boolean cut, entering;

    ShotPlanner(Vec anchor, Pose previous, Pose original, double bearing, CinematicConfig config,
                Scene lastScene, List<Pose> recentViews, int subjectTakes, Shot.Type lastType, Random random) {
        this.anchor = anchor;
        this.previous = previous;
        this.original = original;
        this.bearing = bearing;
        this.config = config;
        this.lastScene = lastScene;
        this.recentViews = List.copyOf(recentViews);
        this.lastType = lastType;
        this.random = random;
        triedPlayer = config.subject == CinematicConfig.Subject.SCENERY;
        triedScenery = config.subject == CinematicConfig.Subject.PLAYER;
        if (triedPlayer || triedScenery) sceneryFirst = triedPlayer;
        else if (lastScene != null && subjectTakes >= (lastScene.scenery() ? 4 : 2)) sceneryFirst = !lastScene.scenery();
        else sceneryFirst = random.nextDouble() < 0.75;
    }

    boolean exhausted() { return search == null && triedPlayer && triedScenery; }

    static double preparationSeconds(CinematicConfig config) {
        int player = config.subject == CinematicConfig.Subject.SCENERY ? 0 : ShotSearch.playerAttempts(config);
        int scenery = config.subject == CinematicConfig.Subject.PLAYER ? 0
            : Scene.PROBES + SCENERY_CANDIDATES * (SCENERY_ATTEMPTS + 1);
        // Two planning steps per tick, including a fallback after a rejected subject.
        return Math.max(4, (player + scenery + 2) / 40.0 + 0.5);
    }

    Plan next(Geometry geometry) {
        if (search == null) {
            if (!triedPlayer && (!sceneryFirst || triedScenery)) {
                triedPlayer = true;
                selected = new Scene(anchor, original.position(), Scene.Kind.PLAYER, 0);
                cut = lastScene != null;
                entering = true;
            } else if (!triedScenery) {
                if (probe < Scene.PROBES) {
                    Scene scene = geometry.scenery(anchor, original, bearing, probe++);
                    if (scene != null && scene.scenery() && scene.near(anchor) && !ShotSearch.stylesFor(scene, config).isEmpty()
                        && candidates.stream().noneMatch(other -> other.focus().distance(scene.focus()) < 3
                            && other.vantage().distance(scene.vantage()) < 3)) candidates.add(scene);
                    if (probe == Scene.PROBES) candidates.sort(Comparator.comparingDouble(this::score).reversed());
                    return null;
                }
                if (candidate >= Math.min(SCENERY_CANDIDATES, candidates.size())) { triedScenery = true; return null; }
                selected = candidates.get(candidate++);
                cut = true;
                entering = true;
            } else return null;
            search = new ShotSearch(selected, previous, bearing, config, lastType, entering, cut, random);
            attempts = 0;
        }
        Shot shot = search.next(geometry.forScene(selected));
        if (shot != null && Scene.inRange(shot.start(), anchor) && Scene.inRange(shot.control(), anchor)
            && Scene.inRange(shot.end(), anchor)) return new Plan(shot, selected, cut, entering);
        if (search.exhausted() || selected.scenery() && ++attempts >= SCENERY_ATTEMPTS) search = null;
        return null;
    }

    private double score(Scene scene) {
        Pose view = Pose.lookingAt(scene.vantage(), scene.focus());
        double penalty = 0;
        for (int i = 0; i < recentViews.size(); i++) {
            Pose recent = recentViews.get(i);
            double turn = Math.hypot(Math.IEEEremainder(view.yaw() - recent.yaw(), 360), view.pitch() - recent.pitch());
            double likeness = Math.max(0, 1 - view.position().distance(recent.position()) / 8) * Math.max(0, 1 - turn / 60);
            penalty = Math.max(penalty, 4.5 * likeness * (i + 1) / recentViews.size());
        }
        return scene.interest() - penalty;
    }
}
