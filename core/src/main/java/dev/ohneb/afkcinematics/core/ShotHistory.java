package dev.ohneb.afkcinematics.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Remembers started takes, so failed candidates never spend the sequence's variety. */
final class ShotHistory {
    private record Take(Scene scene, Shot shot, Pose view) { }
    private final List<Take> takes = new ArrayList<>();

    void add(Scene scene, Shot shot) {
        takes.add(new Take(scene, shot, shot.sample(0)));
        if (takes.size() > 12) takes.removeFirst();
    }

    boolean empty() { return takes.isEmpty(); }
    Shot.Type lastType() { return empty() ? null : takes.getLast().shot().type(); }

    boolean sceneryFirst(int playerShare, Random random) {
        int players = (int)takes.stream().filter(take -> !take.scene().scenery()).count();
        double wanted = (takes.size() + 1) * Math.clamp(playerShare, 0, 100) / 100.0;
        return players + random.nextDouble() >= wanted;
    }

    int lastUse(Shot.Type type) {
        for (int i = takes.size() - 1; i >= 0; i--) if (takes.get(i).shot().type() == type) return i;
        return -1;
    }

    double sceneScore(Scene scene) {
        double penalty = viewPenalty(Pose.lookingAt(scene.vantage(), scene.focus()), scene.scenery());
        int sameKind = 0;
        for (Take take : takes) if (take.scene().kind() == scene.kind()) sameKind++;
        // Vary altitude and medium as well as position; unfamiliar kinds still need a good view.
        return scene.interest() - penalty - Math.min(4, sameKind * 0.8);
    }

    double viewPenalty(Pose view, boolean scenery) {
        double penalty = 0;
        for (int i = 0; i < takes.size(); i++) {
            Take take = takes.get(i);
            if (take.scene().scenery() != scenery) continue;
            Pose previous = take.view();
            double turn = Math.hypot(Math.IEEEremainder(view.yaw() - previous.yaw(), 360), view.pitch() - previous.pitch());
            double likeness = Math.max(0, 1 - view.position().distance(previous.position()) / 16)
                * Math.max(0, 1 - turn / 90);
            penalty = Math.max(penalty, 10 * likeness * (i + 1) / takes.size());
        }
        return penalty;
    }
}
