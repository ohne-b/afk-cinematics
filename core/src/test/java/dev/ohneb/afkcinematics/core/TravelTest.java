package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class TravelTest {
    private static final Vec ZERO = new Vec(0, 0, 0);
    private static final Geometry OPEN = new Geometry() {
        public boolean clear(Vec a, Vec b, double margin) { return true; }
        public boolean visible(Vec a, Vec b, double margin) { return true; }
    };

    @Test void travellingCameraRoundsNarrowCornersWithoutEndingOrCrossingWalls() {
        Geometry corridor = new Geometry() {
            private boolean inside(Vec p, double margin) {
                return Math.abs(p.x()) + margin < .5 && p.z() + margin < .5
                    || Math.abs(p.z()) + margin < .5 && p.x() - margin > -.5;
            }
            private boolean line(Vec a, Vec b, double margin) {
                int steps = Math.max(1, (int)Math.ceil(a.distance(b) * 100));
                for (int i = 0; i <= steps; i++) if (!inside(a.lerp(b, (double)i / steps), margin)) return false;
                return true;
            }
            public boolean clear(Vec a, Vec b, double margin) { return line(a, b, .25 + margin); }
            public boolean visible(Vec a, Vec b, double margin) { return line(a, b, margin); }
        };
        var moving = new MovingGeometry(corridor, new Vec(0, 0, -7), 0);
        moving.course(0);
        Pose local = new Pose(new Vec(0, 0, -5), 0, 0);
        Pose previous = null;
        double previousFade = 0;
        boolean close = false;
        for (int frame = 0; frame < 110; frame++) {
            Vec rider = frame < 35 ? new Vec(0, 0, -7 + frame * .2) : new Vec((frame - 35) * .2, 0, 0);
            if (frame >= 35) moving.course(-90);
            assertTrue(moving.follow(rider, frame * 50_000_000L, false));
            Pose next = moving.apply(local, ZERO, 0, 1, false);
            assertNotNull(next, "Corner ended the view at frame " + frame);
            assertTrue(corridor.clear(next.position(), next.position()));
            if (moving.fade() < 1) assertTrue(corridor.visible(next.position(), rider));
            if (previous != null && !corridor.clear(previous.position(), next.position())) {
                assertEquals(1, previousFade, "Disconnected move revealed its origin");
                assertEquals(1, moving.fade(), "Disconnected move revealed its destination");
            }
            close |= next.position().distance(rider) < 2;
            previous = next;
            previousFade = moving.fade();
            moving.coveredFrame();
        }
        assertTrue(close, "The wide trail never shortened for the corner");
        assertEquals(0, moving.fade(), "Recovery remained black after rounding the corner");
    }

    @Test void obstructionRecoveryWaitsForBothCoveredFramesAndThenWidens() {
        boolean[] wall = {false};
        Geometry world = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) {
                return !wall[0] || Math.max(a.z(), b.z()) + .25 + margin < -4
                    || Math.min(a.z(), b.z()) - .25 - margin > -4;
            }
            public boolean visible(Vec a, Vec b, double margin) {
                return !wall[0] || Math.max(a.z(), b.z()) + margin < -4
                    || Math.min(a.z(), b.z()) - margin > -4;
            }
        };
        var moving = new MovingGeometry(world, ZERO, 0);
        moving.course(0);
        Pose local = new Pose(new Vec(0, 1, -10), 0, 0);
        Pose original = moving.apply(local, ZERO, 0, 1, false);
        wall[0] = true;
        moving.follow(ZERO, 50_000_000, false);
        assertEquals(original, moving.apply(local, ZERO, 0, 1, false));
        assertEquals(1, moving.fade());
        assertEquals(original, moving.apply(local, ZERO, 0, 1, false), "Moved before the origin was covered");
        moving.coveredFrame();
        Pose close = moving.apply(local, ZERO, 0, 1, false);
        assertNotNull(close);
        assertTrue(close.position().z() > -4);
        assertEquals(1, moving.fade());
        assertEquals(close, moving.apply(local, ZERO, 0, 1, false));
        assertEquals(1, moving.fade(), "Revealed before the destination was covered");
        moving.coveredFrame();
        wall[0] = false;
        Pose previous = close;
        for (int frame = 2; frame < 100; frame++) {
            moving.follow(ZERO, frame * 50_000_000L, false);
            Pose next = moving.apply(local, ZERO, 0, 1, false);
            assertNotNull(next);
            assertTrue(next.position().distance(previous.position()) <= .101, "Widening snapped back to the original distance");
            previous = next;
        }
        assertEquals(0, moving.fade());
        assertEquals(original.position().x(), previous.position().x(), 1e-6);
        assertEquals(original.position().z(), previous.position().z(), 1e-6);
        assertFalse(moving.follow(new Vec(40, 0, 0), 5_000_000_000L, false), "Recovery excused a teleport");
    }

    @Test void travelSessionKeepsFilmingWhenOnlyItsPlannedWideViewsAreBlocked() {
        double[] width = {30};
        Geometry world = new Geometry() {
            private boolean sameSide(Vec a, Vec b, double margin) {
                return Math.max(a.x(), b.x()) + margin < -width[0]
                    || Math.min(a.x(), b.x()) - margin > width[0]
                    || Math.abs(a.x()) + margin < width[0] && Math.abs(b.x()) + margin < width[0];
            }
            public boolean clear(Vec a, Vec b, double margin) {
                return sameSide(a, b, .25 + margin);
            }
            public boolean visible(Vec a, Vec b, double margin) {
                return sameSide(a, b, margin);
            }
        };
        var moving = new MovingGeometry(world, ZERO, 0);
        moving.course(0);
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.ESCORT);
        var session = new CinematicSession(ZERO, new Pose(new Vec(0, .4, 0), 0, 0), 0,
            config, moving, new Random(3), Scene.Kind.MINECART);
        for (int frame = 0; frame < 300; frame++) {
            long now = frame * 50_000_000L;
            moving.follow(ZERO, now, false);
            if (frame == 60) { width[0] = 1.5; session.nextShot(); }
            session.tick(now);
            Pose local = session.frame(now);
            assertNotNull(local, "Session rejected the unadjusted travel pose at " + frame);
            assertNotNull(moving.apply(local, session.focus(), session.fade(), session.take(), false), "Applied travel view failed at " + frame);
            session.coveredFrame();
            moving.coveredFrame();
            assertFalse(session.ended());
        }
    }

    @Test void travelViewsKeepTheirOffsetAndFindCloseFallbacks() {
        Geometry narrow = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return a.distance(ZERO) < 14 && b.distance(ZERO) < 14; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
        };
        for (var kind : new Scene.Kind[]{Scene.Kind.BOAT, Scene.Kind.MINECART, Scene.Kind.FLIGHT}) {
            for (var type : Shot.Type.values()) {
                if (!type.travelOnly()) continue;
                var config = new CinematicConfig();
                config.shots = EnumSet.of(type);
                var search = new TravelSearch(ZERO, kind, config, new ShotHistory(), new Random(3));
                ShotPlanner.Plan found = null;
                while (!search.exhausted() && found == null) found = search.next(narrow);
                assertNotNull(found, kind + " " + type);
                assertTrue(found.cut());
                assertEquals(0, found.shot().start().distance(found.shot().sample(10).position()), 1e-10);
                assertEquals(type, found.shot().type());
            }
        }
    }

    @Test void minecartTrailFitsAStraightRailTunnelWithoutChangingOpenViews() {
        Geometry tunnel = new Geometry() {
            private boolean inside(Vec point, double margin) {
                return Math.abs(point.x()) + margin < .5 && point.y() - margin > -1.2 && point.y() + margin < .8;
            }
            public boolean clear(Vec a, Vec b, double margin) { return inside(a, .25 + margin) && inside(b, .25 + margin); }
            public boolean visible(Vec a, Vec b, double margin) { return inside(a, margin) && inside(b, margin); }
        };
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.TRAIL);
        var search = new TravelSearch(ZERO, Scene.Kind.MINECART, config, new ShotHistory(), new Random(3));
        ShotPlanner.Plan found = null;
        while (!search.exhausted() && found == null) found = search.next(tunnel);
        assertNotNull(found, "A straight, one-block-wide rail tunnel needs a centered trailing fallback");
        assertEquals(0, found.shot().start().x(), 1e-10);
        assertEquals(0, found.shot().start().y(), 1e-10);
        assertEquals(5, found.shot().start().distance(ZERO), 1e-10);
        var open = new TravelSearch(ZERO, Scene.Kind.MINECART, config, new ShotHistory(), new Random(3)).next(OPEN);
        assertNotNull(open);
        assertTrue(Math.abs(open.shot().start().x()) > 3, "Open journeys still prefer the wide angled view");
    }

    @Test void movingViewsSweepFromTheLastWorldPositionEvenUnderCover() {
        Geometry wall = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return Math.min(a.x(), b.x()) >= 0 || Math.max(a.x(), b.x()) < 0; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
        };
        var moving = new MovingGeometry(wall, ZERO, 0);
        Pose left = new Pose(new Vec(-1, 2, -3), 0, 0);
        assertNotNull(moving.apply(left, ZERO, 1, 1, false));
        assertTrue(moving.follow(new Vec(3, 0, 0), 100_000_000, false));
        assertNull(moving.apply(left, ZERO, 1, 1, false), "A black frame cannot excuse travel through a wall");
        assertNotNull(moving.apply(left, ZERO, 1, 2, false), "An explicit covered cut may change sides");
        assertFalse(moving.follow(new Vec(50, 0, 0), 200_000_000, false), "Covered cuts cannot excuse a teleport");
    }

    @Test void centeredMinecartViewDoesNotAimThroughTheNextTunnelWall() {
        Geometry corner = new Geometry() {
            private boolean inside(Vec point, double margin) {
                return Math.abs(point.x()) + margin < .5 && point.y() - margin > -1.2
                    && point.y() + margin < .8 && point.z() + margin < 1;
            }
            public boolean clear(Vec a, Vec b, double margin) { return inside(a, .25 + margin) && inside(b, .25 + margin); }
            public boolean visible(Vec a, Vec b, double margin) { return inside(a, margin) && inside(b, margin); }
        };
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.TRAIL);
        var search = new TravelSearch(ZERO, Scene.Kind.MINECART, config, new ShotHistory(), new Random(3));
        ShotPlanner.Plan found = null;
        while (!search.exhausted() && found == null) found = search.next(corner);
        assertNotNull(found, "The rider and camera are visible before the bend; an imaginary lead point must not reject them");
        assertEquals(ZERO, found.shot().focusStart());
    }

    @Test void highFlightFramingLeavesRoomForFollowingLag() {
        Geometry range = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) { return a.distance(b) + 2 * margin <= 32; }
        };
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.HIGH_TRAIL);
        var moving = new MovingGeometry(range, ZERO, 0);
        moving.course(0);
        var search = new TravelSearch(ZERO, Scene.Kind.FLIGHT, config, new ShotHistory(), new Random(3));
        var plan = search.next(moving);
        assertNotNull(plan);
        assertTrue(moving.follow(new Vec(0, 0, 2), 1, false));
        assertNotNull(moving.apply(plan.shot().sample(0), plan.shot().focus(0), 0, 1, false),
            "Acceleration in open air must not immediately exhaust the sightline range");
    }

    @Test void screenshotsHoldTheWorldPoseAndResumeSmoothly() {
        var moving = new MovingGeometry(OPEN, ZERO, 0);
        Pose local = new Pose(new Vec(3, 2, -7), 0, 0);
        Pose before = moving.apply(local, ZERO, 0, 1, false);
        assertTrue(moving.follow(new Vec(4, 0, 0), 100_000_000, true));
        assertEquals(before, moving.apply(local, ZERO, 0, 1, true));
        assertTrue(moving.follow(new Vec(4, 0, 0), 110_000_000, false));
        Pose resumed = moving.apply(local, ZERO, 0, 1, false);
        assertTrue(resumed.position().distance(before.position()) < 0.5);
        assertTrue(resumed.position().distance(before.position()) > 0);
    }

    @Test void courseChangesEaseAcrossNorthAndReversalsWaitForCover() {
        var moving = new MovingGeometry(OPEN, ZERO, 0);
        moving.course(170);
        Pose local = new Pose(new Vec(0, 2, -10), 0, 0);
        Pose previous = moving.apply(local, ZERO, 0, 1, false);
        moving.course(-170);
        assertFalse(moving.needsReframe(Scene.Kind.BOAT), "Crossing north must take the short turn");
        for (long now = 20_000_000; now <= 3_000_000_000L; now += 20_000_000) {
            assertTrue(moving.follow(ZERO, now, false));
            Pose next = moving.apply(local, ZERO, 0, 1, false);
            assertTrue(next.position().distance(previous.position()) < .08, "The following view must turn gradually");
            previous = next;
        }
        assertEquals(-170, Math.IEEEremainder(previous.yaw(), 360), .1);
        moving.course(10);
        assertTrue(moving.needsReframe(Scene.Kind.BOAT));
        assertFalse(moving.needsReframe(Scene.Kind.BOAT), "A reversal requests one cut, not a queue of cuts");
        assertTrue(moving.follow(ZERO, 3_020_000_000L, true));
        assertEquals(previous, moving.apply(local, ZERO, 0, 1, true));
        Pose covered = moving.apply(local, ZERO, 1, 1, false);
        assertEquals(previous.position(), covered.position());
        Pose destination = moving.apply(local, ZERO, 1, 2, false);
        assertTrue(destination.position().distance(covered.position()) > 19);
        assertEquals(10, destination.yaw(), .001);
        assertFalse(moving.needsReframe(Scene.Kind.BOAT));
    }

    @Test void captureStillRequiresVisibilityOfTheMovingSubject() {
        Geometry hidden = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) { return b.x() < .5; }
        };
        var moving = new MovingGeometry(hidden, ZERO, 0);
        Pose local = new Pose(new Vec(0, 2, -10), 0, 0);
        assertNotNull(moving.apply(local, ZERO, 0, 1, false));
        assertTrue(moving.follow(new Vec(1, 0, 0), 100_000_000L, true));
        assertNull(moving.apply(local, ZERO, 0, 1, true));
    }

    @Test void minecartBendsKeepTheTakeButReversalsStillRequestCover() {
        var moving = new MovingGeometry(OPEN, ZERO, 0);
        moving.course(0);
        Pose local = new Pose(new Vec(0, 2, -10), 0, 0);
        Pose previous = moving.apply(local, ZERO, 0, 1, false);
        moving.course(90);
        for (long now = 20_000_000; now <= 6_000_000_000L; now += 20_000_000) {
            assertFalse(moving.needsReframe(Scene.Kind.MINECART), "A normal rail bend must not request another shot");
            assertTrue(moving.follow(ZERO, now, false));
            Pose next = moving.apply(local, ZERO, 0, 1, false);
            assertNotNull(next);
            assertTrue(next.position().distance(previous.position()) < .08, "Rail bends still turn smoothly");
            previous = next;
        }
        assertEquals(90, previous.yaw(), 1);
        moving.course(-90);
        assertTrue(moving.needsReframe(Scene.Kind.MINECART));
        assertFalse(moving.needsReframe(Scene.Kind.MINECART));
    }
}
