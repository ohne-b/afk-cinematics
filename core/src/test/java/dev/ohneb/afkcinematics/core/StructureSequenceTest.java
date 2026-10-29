package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class StructureSequenceTest {
    private static final Vec ANCHOR = new Vec(0, 1, 0);
    private static final Pose ENTRY = new Pose(new Vec(0, 2, 0), 0, 0);
    private static final Scene FRONT = new Scene(new Vec(8, 3, 0), new Vec(0, 3, 0), Scene.Kind.STRUCTURE, 9);
    private static final Scene SIDE = new Scene(new Vec(8, 3, 1), new Vec(8, 3, -8), Scene.Kind.STRUCTURE, 9);
    private static final Scene GARDEN = new Scene(new Vec(-8, 1, 0), new Vec(-8, 2, -8), Scene.Kind.GROUND, 6);

    @Test void aBuildingCanGetTwoViewsBeforeReturningToItsSurroundings() {
        var first = plan(null, List.of(), true);
        assertEquals(Scene.Kind.STRUCTURE, first.scene().kind());
        var history = List.of(new ShotPlanner.View(Pose.lookingAt(FRONT.vantage(), FRONT.focus()), FRONT.kind()));
        var second = plan(FRONT, history, true);
        assertEquals(SIDE, second.scene());
        history = List.of(history.getFirst(), new ShotPlanner.View(Pose.lookingAt(SIDE.vantage(), SIDE.focus()), SIDE.kind()));
        assertEquals(GARDEN, plan(SIDE, history, true).scene());
    }

    @Test void aBuildingRemainsAFallbackWhenItsSurroundingsAreBlocked() {
        var history = List.of(
            new ShotPlanner.View(Pose.lookingAt(FRONT.vantage(), FRONT.focus()), FRONT.kind()),
            new ShotPlanner.View(Pose.lookingAt(SIDE.vantage(), SIDE.focus()), SIDE.kind()));
        assertEquals(Scene.Kind.STRUCTURE, plan(SIDE, history, false).scene().kind());
    }

    private static ShotPlanner.Plan plan(Scene previous, List<ShotPlanner.View> history, boolean garden) {
        var config = new CinematicConfig();
        config.subject = CinematicConfig.Subject.SCENERY;
        config.shots = EnumSet.of(Shot.Type.STILL);
        var planner = new ShotPlanner(ANCHOR, ENTRY, ENTRY, 0, config, previous, history, 1, null, new Random(4));
        Geometry geometry = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return garden || a.x() >= 0 && b.x() >= 0; }
            public boolean visible(Vec a, Vec b, double margin) { return garden || b.x() >= 0; }
            public Scene scenery(Vec anchor, Pose original, double bearing, int probe) {
                return switch (probe) { case 0 -> FRONT; case 1 -> SIDE; case 2 -> GARDEN; default -> null; };
            }
        };
        for (int i = 0; i < 300 && !planner.exhausted(); i++) {
            var result = planner.next(geometry);
            if (result != null) return result;
        }
        fail("No usable view remained");
        return null;
    }
}
