package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ShotSequenceTest {
    private static final Vec ANCHOR = new Vec(0, 1, 0);
    private static final Pose ENTRY = new Pose(new Vec(0, 1.6, 0), 0, 0);
    private static final Scene FIRST = new Scene(new Vec(-8, 10, 0), new Vec(-8, 12, -8), true, 8);
    private static final Scene SECOND = new Scene(new Vec(8, 10, 0), new Vec(8, 12, -8), true, 7.9);
    private static final Scene THIRD = new Scene(new Vec(0, 10, 8), new Vec(0, 12, 0), true, 7.8);

    @Test void aPreparedTakeDoesNotFreezeOrReplaceTheCurrentMotionEarly() {
        var accepted = new ArrayList<Shot>();
        var session = session(geometry(accepted, new boolean[1]), Shot.Type.ARC);
        long now = reachSecondPlan(session, accepted);
        Pose before = session.current();
        assertTrue(before.position().distance(accepted.getFirst().end()) > 0.1,
            "Planning began only after the current take stopped");
        for (int tick = 0; tick < 20; tick++) frame(session, now += 50_000_000L);
        assertTrue(session.current().position().distance(before.position()) > 0.05,
            "Preparing the next take froze the current camera");
        assertEquals(0, session.fade(), "The prepared take started before the current one finished");
    }

    @Test void anObstructedPreparedPathIsRecheckedAtHandoff() {
        var accepted = new ArrayList<Shot>();
        boolean[] blocked = {false};
        var session = session(geometry(accepted, blocked), Shot.Type.ARC);
        long now = reachSecondPlan(session, accepted);
        blocked[0] = true;
        for (int tick = 0; tick < 140 && !session.ended(); tick++) {
            now += 50_000_000L;
            session.tick(now);
            session.frame(now);
            session.coveredFrame();
        }
        assertTrue(session.ended(), "A stale prepared path was installed without revalidation");
    }

    @Test void nextShotDuringAdvancePlanningStartsFromTheCurrentPose() {
        var accepted = new ArrayList<Shot>();
        var session = session(geometry(accepted, new boolean[1]), Shot.Type.ARC);
        long now = reachSecondPlan(session, accepted);
        Pose before = session.current();
        session.nextShot();
        frame(session, now + 50_000_000L);
        assertTrue(session.current().position().distance(before.position()) < 0.01,
            "Next shot jumped to the preplanned endpoint");
        assertTrue(accepted.size() >= 3, "Next shot reused a plan from a different starting pose");
    }

    @Test void relatedSceneryTakesContinueWithoutAnotherCoveredCut() {
        var session = session(geometry(new ArrayList<>(), new boolean[1]), Shot.Type.ARC, Shot.Type.PAN);
        int covers = 0;
        double previous = 0;
        for (int tick = 0; tick < 500; tick++) {
            frame(session, tick * 50_000_000L);
            if (session.fade() == 1 && previous < 1) covers++;
            previous = session.fade();
        }
        assertEquals(1, covers, "Every related take faded to black again");
    }

    @Test void recentViewsPreventAnImmediateABACycleWithoutExhaustingTheScene() {
        var session = session(geometry(new ArrayList<>(), new boolean[1]), Shot.Type.STILL);
        var views = new ArrayList<Vec>();
        for (int tick = 0; tick < 1300; tick++) {
            frame(session, tick * 50_000_000L);
            if (!session.scenery() || session.fade() != 1) continue;
            Vec point = session.current().position();
            if (views.isEmpty() || views.getLast().distance(point) > 2) views.add(point);
        }
        assertTrue(views.size() >= 3);
        assertEquals(FIRST.vantage(), views.get(0));
        assertEquals(SECOND.vantage(), views.get(1));
        assertEquals(THIRD.vantage(), views.get(2), "The third take returned immediately to the first view");
    }

    private static long reachSecondPlan(CinematicSession session, List<Shot> accepted) {
        for (int tick = 0; tick < 600; tick++) {
            long now = tick * 50_000_000L;
            frame(session, now);
            if (accepted.size() >= 2) return now;
        }
        fail("No second plan was prepared");
        return 0;
    }

    private static void frame(CinematicSession session, long now) {
        session.tick(now);
        Pose pose = session.frame(now);
        if (session.showing()) assertNotNull(pose);
        assertFalse(session.ended());
        session.coveredFrame();
    }

    private static CinematicSession session(Geometry geometry, Shot.Type... types) {
        var config = new CinematicConfig();
        config.subject = CinematicConfig.Subject.SCENERY;
        config.pace = CinematicConfig.Pace.SHORT;
        config.shots = EnumSet.copyOf(List.of(types));
        return new CinematicSession(ANCHOR, ENTRY, 0, config, geometry, new Random(4));
    }

    private static Geometry geometry(List<Shot> accepted, boolean[] blocked) {
        return new Geometry() {
            public boolean clear(Vec from, Vec to, double margin) { return true; }
            public boolean visible(Vec from, Vec to, double margin) { return true; }
            public Scene scenery(Vec anchor, Pose original, double bearing, int probe) {
                return switch (probe) { case 0 -> FIRST; case 1 -> SECOND; case 2 -> THIRD; default -> null; };
            }
            public boolean valid(Shot shot, Vec entry) {
                if (blocked[0]) return false;
                if (!accepted.contains(shot)) accepted.add(shot);
                return Geometry.super.valid(shot, entry);
            }
        };
    }
}
