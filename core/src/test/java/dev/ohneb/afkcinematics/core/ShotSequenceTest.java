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
    private static final Scene FIRST = new Scene(new Vec(-8, 10, 0), new Vec(-8, 12, -8), Scene.Kind.LANDSCAPE, 8);
    private static final Scene SECOND = new Scene(new Vec(8, 10, 0), new Vec(8, 12, -8), Scene.Kind.LANDSCAPE, 7.9);
    private static final Scene THIRD = new Scene(new Vec(0, 10, 8), new Vec(0, 12, 0), Scene.Kind.LANDSCAPE, 7.8);

    @Test void successfulTakesUseEveryAvailableMoveBeforeRepeating() {
        var config = new CinematicConfig();
        config.subject = CinematicConfig.Subject.SCENERY;
        config.shots = EnumSet.of(Shot.Type.DOLLY, Shot.Type.DRIFT, Shot.Type.CRANE, Shot.Type.PAN);
        var history = new ShotHistory();
        var used = EnumSet.noneOf(Shot.Type.class);
        var world = geometry(new ArrayList<>(), new boolean[1]);
        for (int take = 0; take < config.shots.size(); take++) {
            var planner = new ShotPlanner(ANCHOR, ENTRY, ENTRY, 0, config, history, new Random(take));
            ShotPlanner.Plan plan = null;
            for (int step = 0; step < 200 && plan == null && !planner.exhausted(); step++) plan = planner.next(world);
            assertNotNull(plan);
            assertTrue(used.add(plan.shot().type()), "Repeated a move before using the remaining available ones");
            history.add(plan.scene(), plan.shot());
        }
        assertEquals(config.shots, used);
    }

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

    @Test void rejectedPlayerViewsLeaveTimeToPrepareTheSceneryFallback() {
        var accepted = new ArrayList<Shot>();
        Geometry geometry = new Geometry() {
            public boolean clear(Vec from, Vec to, double margin) { return true; }
            public boolean visible(Vec from, Vec to, double margin) { return true; }
            public Scene scenery(Vec anchor, Pose original, double bearing, int probe) { return probe == 0 ? FIRST : null; }
            public boolean valid(Shot shot, Vec entry) {
                if (shot.focusStart().y() < 5) return false;
                if (!accepted.contains(shot)) accepted.add(shot);
                return true;
            }
        };
        var config = new CinematicConfig();
        config.pace = CinematicConfig.Pace.SHORT;
        var session = new CinematicSession(ANCHOR, ENTRY, 0, config, geometry, new Random(5));
        int stillFrames = 0;
        Vec previous = ENTRY.position();
        for (int tick = 0; tick < 2200; tick++) {
            long now = tick * 50_000_000L;
            frame(session, now);
            if (!session.settled()) { stillFrames = 0; continue; }
            Shot active = accepted.stream().filter(shot -> shot.sample(shot.duration()).position().distance(session.current().position()) < 1e-10)
                .findFirst().orElse(null);
            if (active != null && active.type() != Shot.Type.STILL && active.type() != Shot.Type.PAN
                && previous.distance(session.current().position()) < 1e-10) stillFrames++;
            else stillFrames = 0;
            assertTrue(stillFrames < 2, "The current take held its endpoint while waiting for the scenery fallback");
            previous = session.current().position();
        }
        assertTrue(accepted.size() >= 5, "The fixture never reached a forced player-first search");
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

    @Test void rejectedSuccessorsDoNotCutTheCurrentTakeShort() {
        Shot[] first = {null};
        int[] rejected = {0};
        var geometry = new Geometry() {
            public boolean clear(Vec from, Vec to, double margin) { return true; }
            public boolean visible(Vec from, Vec to, double margin) { return true; }
            public boolean valid(Shot shot, Vec entry) {
                if (first[0] == null) first[0] = shot;
                if (shot == first[0]) return true;
                rejected[0]++;
                return false;
            }
        };
        var config = new CinematicConfig();
        config.subject = CinematicConfig.Subject.PLAYER;
        config.pace = CinematicConfig.Pace.SHORT;
        config.shots = EnumSet.of(Shot.Type.ARC);
        var session = new CinematicSession(ANCHOR, ENTRY, 0, config, geometry, new Random(4));
        long now = 0;
        while (!session.settled() && now < 7_000_000_000L) frame(session, now += 50_000_000L);
        assertTrue(session.settled());
        long finish = now + Math.round(first[0].duration() * 1e9);
        while (now < finish - 1_500_000_000L) frame(session, now += 50_000_000L);
        Pose before = session.current();
        while (now < finish - 500_000_000L) frame(session, now += 50_000_000L);
        assertTrue(rejected[0] >= 10, "Successor planning did not exhaust its candidates");
        assertTrue(session.current().position().distance(before.position()) > 0.001,
            "The valid current take froze after successor planning failed");
        while (now < finish + 100_000_000L) {
            now += 50_000_000L;
            session.tick(now);
            session.frame(now);
        }
        assertTrue(session.ended());
    }

    @Test void nextShotDuringAdvancePlanningStartsFromTheCurrentPose() {
        var accepted = new ArrayList<Shot>();
        var session = session(geometry(accepted, new boolean[1]), Shot.Type.ARC);
        long now = reachSecondPlan(session, accepted);
        Pose before = session.current();
        session.nextShot();
        frame(session, now + 50_000_000L);
        assertTrue(session.current().position().distance(before.position()) < 0.1,
            "Next shot jumped to the preplanned endpoint");
        for (int tick = 2; tick < 100 && accepted.size() < 3; tick++) frame(session, now + tick * 50_000_000L);
        assertTrue(accepted.size() >= 3, "Next shot reused a plan from a different starting pose");
    }

    @Test void aChangeOfTakeUsesACutInsteadOfStoppingAndReversingInPlace() {
        var session = session(geometry(new ArrayList<>(), new boolean[1]), Shot.Type.ARC, Shot.Type.PAN);
        int covers = 0;
        double previous = 0;
        for (int tick = 0; tick < 500; tick++) {
            frame(session, tick * 50_000_000L);
            if (session.fade() == 1 && previous < 1) covers++;
            previous = session.fade();
        }
        assertTrue(covers >= 2, "Independent takes were joined into a stop-and-turn movement");
    }

    @Test void coveredTransitionsKeepBothTakesMovingAndRespectCaptureHolds() {
        var session = session(geometry(new ArrayList<>(), new boolean[1]), Shot.Type.ARC);
        long now = 0;
        Pose previous = ENTRY;
        double opacity = 0;
        int take = 0, movingOut = 0, movingIn = 0;
        boolean captured = false;
        for (int tick = 0; tick < 900; tick++) {
            frame(session, now += 50_000_000L);
            double fade = session.fade();
            if (take == session.take() && take > 0 && fade > 0 && fade < 1 && opacity > 0 && opacity < 1) {
                assertTrue(session.current().position().distance(previous.position()) > 1e-8,
                    "A visible fade froze the moving camera");
                if (fade > opacity) {
                    movingOut++;
                    if (!captured) {
                        Pose held = session.current();
                        session.beginCapture(now);
                        assertEquals(held, session.frame(now + 2_000_000_000L));
                        session.endCapture(now += 2_000_000_000L);
                        assertEquals(held, session.frame(now), "Releasing a capture skipped outgoing motion");
                        assertEquals(fade, session.fade(), 1e-12);
                        captured = true;
                    }
                } else movingIn++;
            }
            previous = session.current();
            opacity = session.fade();
            take = session.take();
        }
        assertTrue(movingOut > 0 && movingIn > 0 && captured);
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
