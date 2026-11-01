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

    @Test void wildlifeStaysRareAndRemembersTheLastSpeciesBeyondRecentHistory() {
        var config = new CinematicConfig();
        config.subject = CinematicConfig.Subject.SCENERY;
        var history = new ShotHistory();
        var animal = new Scene(FIRST.focus(), FIRST.vantage(), Scene.Kind.WILDLIFE, 8,
            new Scene.Animal(1, java.util.UUID.randomUUID(), "minecraft:fox"));
        Shot shot = new Shot(Shot.Type.STILL, FIRST.vantage(), FIRST.vantage(), FIRST.vantage(), FIRST.focus(), FIRST.focus(), 10);
        for (int i = 0; i < 3; i++) history.add(FIRST, shot);
        assertTrue(history.wildlifeReady());
        history.add(animal, shot);
        for (int i = 0; i < 5; i++) { assertFalse(history.wildlifeReady()); history.add(FIRST, shot); }
        assertTrue(history.wildlifeReady());
        for (int i = 0; i < 12; i++) history.add(FIRST, shot);
        assertEquals("minecraft:fox", history.lastSpecies());
        int[] probes = {0};
        var world = new Geometry() {
            public boolean clear(Vec a, Vec b, double m) { return true; }
            public boolean visible(Vec a, Vec b, double m) { return true; }
            public Scene scenery(Vec a, Pose p, double b, int i) { return FIRST; }
            public Scene wildlife(Vec a, double b, String excluded, int i) { probes[0]++; assertEquals("minecraft:fox", excluded); return null; }
        };
        int attempts = 200;
        for (int i = 0; i < attempts; i++) {
            var planner = new ShotPlanner(ANCHOR, ENTRY, 0, config, history, new Random(i * 31L));
            for (int j = 0; j < 100 && !planner.exhausted(); j++) if (planner.next(world) != null) break;
        }
        assertTrue(probes[0] > 0 && probes[0] < attempts * 8 / 3, "Wildlife became a quota instead of an occasional option");
        config.wildlife = false;
        int before = probes[0];
        var planner = new ShotPlanner(ANCHOR, ENTRY, 0, config, history, new Random(4));
        for (int j = 0; j < 100 && !planner.exhausted(); j++) if (planner.next(world) != null) break;
        assertEquals(before, probes[0]);
    }

    @Test void successfulTakesUseEveryAvailableMoveBeforeRepeating() {
        var config = new CinematicConfig();
        config.subject = CinematicConfig.Subject.SCENERY;
        config.shots = EnumSet.of(Shot.Type.DOLLY, Shot.Type.DRIFT, Shot.Type.CRANE, Shot.Type.PAN);
        var history = new ShotHistory();
        var used = EnumSet.noneOf(Shot.Type.class);
        var world = geometry(new ArrayList<>(), new boolean[1]);
        for (int take = 0; take < config.shots.size(); take++) {
            var planner = new ShotPlanner(ANCHOR, ENTRY, 0, config, history, new Random(take));
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
        assertEquals(1, session.take(), "The prepared take started before the current one finished");
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
        config.pace = CinematicConfig.Pace.QUICK;
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
        for (var transition : CinematicConfig.Transition.values()) {
            var accepted = new ArrayList<Shot>();
            boolean[] blocked = {false};
            var session = session(geometry(accepted, blocked), transition, Shot.Type.ARC);
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

    @Test void nextBeforeTheHandoffDiscardsTheOldPlanBeforeAnotherTickOrCaptureRelease() {
        for (var transition : CinematicConfig.Transition.values()) {
            for (boolean capture : new boolean[]{false, true}) {
                var accepted = new ArrayList<Shot>();
                var session = session(geometry(accepted, new boolean[1]), transition, Shot.Type.ARC);
                long now = 0, started = -1;
                while (accepted.size() < 2 && now < 20_000_000_000L) {
                    frame(session, now += 50_000_000L);
                    if (session.take() == 1 && started == -1) started = now;
                }
                assertEquals(2, accepted.size());
                long handoff = started + Math.round(accepted.getFirst().duration() * 1e9);
                while (now + 50_000_000L < handoff) assertNotNull(session.frame(now += 50_000_000L));
                if (capture) session.beginCapture(now);
                session.nextShot();
                if (capture) session.endCapture(now += 2_000_000_000L);
                assertNotNull(session.frame(now += 100_000_000L));
                assertEquals(1, session.take(), "Next applied the old pending plan before the next tick");
                for (int tick = 0; tick < 80; tick++) {
                    frame(session, now += 50_000_000L);
                    session.coveredFrame();
                }
                assertEquals(3, accepted.size(), "Next did not replace the discarded plan exactly once");
                assertEquals(2, session.take(), "Next caused a second unintended cut");
            }
        }
    }

    @Test void quickTakesCutAtTheirDeadlineWithoutIntermediateFrames() {
        var accepted = new ArrayList<Shot>();
        var config = new CinematicConfig();
        config.subject = CinematicConfig.Subject.SCENERY;
        config.pace = CinematicConfig.Pace.QUICK;
        config.shots = EnumSet.of(Shot.Type.ARC);
        var session = new CinematicSession(ANCHOR, ENTRY, 0, config, geometry(accepted, new boolean[1]), new Random(4));
        int take = 0;
        long started = 0;
        for (int tick = 0; tick < 900; tick++) {
            long now = tick * 50_000_000L;
            frame(session, now);
            if (take == session.take()) continue;
            if (take > 0) assertEquals(accepted.get(take - 1).duration(), (now - started) / 1e9, .051);
            take = session.take();
            started = now;
            assertEquals(accepted.get(take - 1).sample(0), session.current());
        }
        assertTrue(take >= 4);
    }

    @Test void fadesKeepMovingAndWaitForBothOpaqueFramesAcrossCaptureHolds() {
        var config = new CinematicConfig();
        config.subject = CinematicConfig.Subject.SCENERY;
        config.shots = EnumSet.of(Shot.Type.ARC);
        config.pace = CinematicConfig.Pace.QUICK;
        config.transition = CinematicConfig.Transition.FADE;
        var session = new CinematicSession(ANCHOR, ENTRY, 0, config, geometry(new ArrayList<>(), new boolean[1]), new Random(4));
        long now = 0;
        while (session.fadeOpacity() == 0 && now < 20_000_000_000L) frame(session, now += 50_000_000L);
        assertTrue(session.fadeOpacity() > 0 && session.fadeOpacity() < 1);
        Pose held = session.current();
        double opacity = session.fadeOpacity();
        session.beginCapture(now);
        session.beginCapture(now);
        assertEquals(held, session.frame(now + 2_000_000_000L));
        assertEquals(opacity, session.fadeOpacity());
        session.endCapture(now + 2_000_000_000L);
        assertEquals(held, session.frame(now + 3_000_000_000L));
        session.endCapture(now += 3_000_000_000L);
        assertEquals(held, session.frame(now));
        assertEquals(opacity, session.fadeOpacity());
        frame(session, now += 50_000_000L);
        assertTrue(held.position().distance(session.current().position()) > .001, "Fade froze the outgoing move");
        for (int i = 0; i < 10; i++) frame(session, now += 50_000_000L);
        assertEquals(1, session.fadeOpacity());
        assertEquals(1, session.take(), "A timer alone committed the covered cut");
        session.coveredFrame();
        frame(session, now += 50_000_000L);
        assertEquals(2, session.take());
        assertEquals(1, session.fadeOpacity());
        for (int i = 0; i < 6; i++) frame(session, now += 50_000_000L);
        assertEquals(1, session.fadeOpacity(), "Destination was revealed before its opaque frame");
        session.coveredFrame();
        frame(session, now += 100_000_000L);
        assertTrue(session.fadeOpacity() > 0 && session.fadeOpacity() < 1);
        frame(session, now += 300_000_000L);
        assertEquals(0, session.fadeOpacity());
    }

    @Test void aReadyCutWaitsForNestedCaptureReleaseWithoutSkippingTime() {
        var accepted = new ArrayList<Shot>();
        var session = session(geometry(accepted, new boolean[1]), Shot.Type.ARC);
        long now = reachSecondPlan(session, accepted);
        session.nextShot();
        for (int i = 0; i < 40; i++) session.tick(now += 50_000_000L);
        Pose held = session.current();
        int take = session.take();
        session.beginCapture(now);
        session.beginCapture(now);
        session.tick(now + 2_000_000_000L);
        assertEquals(held, session.frame(now + 2_000_000_000L));
        session.endCapture(now + 2_000_000_000L);
        assertEquals(held, session.frame(now + 3_000_000_000L));
        assertEquals(take, session.take());
        session.endCapture(now += 3_000_000_000L);
        assertNotNull(session.frame(now));
        assertEquals(take + 1, session.take());
        Pose cut = session.current();
        assertEquals(cut, session.frame(now));
        assertTrue(session.frame(now + 50_000_000L).position().distance(cut.position()) > .001);
    }

    @Test void travelCutsUseTheDestinationWithoutSweepingBetweenUnrelatedAngles() {
        Vec zero = new Vec(0, 0, 0);
        Geometry world = new Geometry() {
            public boolean clear(Vec a, Vec b, double m) { return a.distance(b) < .5; }
            public boolean visible(Vec a, Vec b, double m) { return true; }
        };
        var moving = new MovingGeometry(world, zero, 0);
        moving.course(0);
        moving.framing(Scene.Kind.FOLLOW, true);
        var config = new CinematicConfig();
        config.pace = CinematicConfig.Pace.QUICK;
        config.shots = EnumSet.of(Shot.Type.TRAIL, Shot.Type.ESCORT);
        var session = new CinematicSession(zero, Pose.lookingAt(new Vec(2, 2, -5), zero), 0,
            config, moving, new Random(4), Scene.Kind.FOLLOW);
        Pose previous = null;
        int take = 0, cuts = 0;
        for (int tick = 0; tick < 900; tick++) {
            long now = tick * 50_000_000L;
            moving.follow(zero, now, false, true);
            session.tick(now);
            Pose local = session.frame(now);
            assertNotNull(local);
            Pose applied = moving.apply(local, session.focus(), session.take(), false);
            assertNotNull(applied);
            if (previous != null && !world.clear(previous.position(), applied.position())) {
                assertEquals(take + 1, session.take());
                cuts++;
            }
            previous = applied;
            take = session.take();
        }
        assertTrue(cuts >= 3);
    }

    @Test void recentViewsPreventAnImmediateABACycleWithoutExhaustingTheScene() {
        var session = session(geometry(new ArrayList<>(), new boolean[1]), Shot.Type.STILL);
        var views = new ArrayList<Vec>();
        for (int tick = 0; tick < 1300; tick++) {
            frame(session, tick * 50_000_000L);
            if (!session.scenery()) continue;
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
    }

    private static CinematicSession session(Geometry geometry, Shot.Type... types) {
        return session(geometry, CinematicConfig.Transition.CUT, types);
    }

    private static CinematicSession session(Geometry geometry, CinematicConfig.Transition transition, Shot.Type... types) {
        var config = new CinematicConfig();
        config.transition = transition;
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
