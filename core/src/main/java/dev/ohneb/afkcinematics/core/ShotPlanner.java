package dev.ohneb.afkcinematics.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Each step performs one terrain probe or validates one shot. */
final class ShotPlanner {
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
                Scene lastScene, List<Pose> recentViews, Shot.Type lastType, Random random) {
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
        sceneryFirst = triedPlayer || lastScene != null && !lastScene.scenery();
    }

    boolean exhausted() { return search == null && triedPlayer && triedScenery; }

    Plan next(Geometry geometry) {
        if (search == null) {
            if (!triedPlayer && (!sceneryFirst || triedScenery)) {
                triedPlayer = true;
                selected = new Scene(anchor, original.position(), false, 0);
                cut = lastScene != null && lastScene.scenery();
                entering = lastScene == null || cut;
            } else if (!triedScenery) {
                if (probe < Scene.PROBES) {
                    Scene scene = geometry.scenery(anchor, original, bearing, probe++);
                    if (scene != null && scene.scenery() && scene.near(anchor)
                        && candidates.stream().noneMatch(other -> other.focus().distance(scene.focus()) < 3
                            && other.vantage().distance(scene.vantage()) < 3)) candidates.add(scene);
                    if (probe == Scene.PROBES) candidates.sort(Comparator.comparingDouble(this::score).reversed());
                    return null;
                }
                if (candidate >= Math.min(4, candidates.size())) { triedScenery = true; return null; }
                selected = candidates.get(candidate++);
                cut = true;
                entering = true;
            } else return null;
            search = new ShotSearch(selected, previous, bearing, config, lastType, entering, cut, random);
            attempts = 0;
        }
        Shot shot = search.next(geometry);
        if (shot != null && Scene.inRange(shot.start(), anchor) && Scene.inRange(shot.control(), anchor)
            && Scene.inRange(shot.end(), anchor)) return new Plan(shot, selected, cut, entering);
        if (search.exhausted() || selected.scenery() && ++attempts >= 21) search = null;
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
