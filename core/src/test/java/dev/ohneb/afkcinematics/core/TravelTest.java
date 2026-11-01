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

    @Test void walkingJumpsStayInsideTheFrameWhileLandingsAndFallsAreFollowed() {
        var moving = new MovingGeometry(OPEN, ZERO, 0);
        moving.framing(Scene.Kind.FOLLOW, true);
        moving.course(0);
        Pose local = new Pose(new Vec(2, 1, -6), 0, 0);
        Pose initial = moving.apply(local, new Vec(0, 0, 1), 0, 1, false);
        for (int frame = 1; frame <= 240; frame++) {
            double phase = (frame % 40) / 40.0;
            double jump = phase < .9 ? 1.25 * Math.sin(phase / .9 * Math.PI) : 0;
            assertTrue(moving.follow(new Vec(0, jump, frame / 15.0), frame * 16_666_667L, false, jump == 0));
            Pose next = moving.apply(local, new Vec(0, 0, 1), 0, 1, false);
            assertNotNull(next);
            assertEquals(initial.position().y(), next.position().y(), .01, "Short jumps must not lift the whole camera rig");
            assertEquals(initial.pitch(), next.pitch(), .01, "Camera and aim must share the filtered height");
        }
        Pose previous = moving.apply(local, new Vec(0, 0, 1), 0, 1, false);
        for (int frame = 241; frame <= 420; frame++) {
            double ground = Math.min(3, (frame - 241) / 30);
            moving.follow(new Vec(0, ground, frame / 15.0), frame * 16_666_667L, false, true);
            Pose next = moving.apply(local, new Vec(0, 0, 1), 0, 1, false);
            assertNotNull(next);
            assertTrue(Math.abs(next.position().y() - previous.position().y()) < .13, "A new landing must ease into the next height");
            previous = next;
        }
        assertEquals(initial.position().y() + 3, previous.position().y(), .01);
        for (int frame = 421; frame <= 600; frame++) {
            moving.follow(new Vec(0, -5, frame / 15.0), frame * 16_666_667L, false, false);
            previous = moving.apply(local, new Vec(0, 0, 1), 0, 1, false);
            assertNotNull(previous);
        }
        assertEquals(initial.position().y() - 5, previous.position().y(), .01, "A sustained fall must not leave the camera at the old height");
    }

    @Test void trackingTheSameJourneyDoesNotDependOnFrameRate() {
        Vec reference = null;
        for (int fps : new int[]{24, 30, 60, 144}) {
            var motion = new FollowMotion(ZERO);
            Vec end = ZERO;
            for (int frame = 1; frame <= fps * 3; frame++) {
                double seconds = (double)frame / fps;
                end = motion.follow(new Vec(seconds * 2, seconds, seconds * 4), 1.0 / fps, true, true);
            }
            if (reference == null) reference = end;
            else assertTrue(end.distance(reference) < 1e-9, "The same movement drifted at " + fps + " FPS");
        }
    }

    @Test void disablingFrontViewsCoversSelectionTurnsAndRecovery() {
        var config = new CinematicConfig();
        config.frontTravel = false;
        config.shots = EnumSet.of(Shot.Type.LEAD);
        var search = new TravelSearch(ZERO, Scene.Kind.FOLLOW, config, new ShotHistory(), new Random(3));
        var plan = search.next(OPEN);
        assertNotNull(plan, "A disabled front-only selection still needs a rear fallback");
        assertTrue(plan.shot().start().z() < 0);
        var moving = new MovingGeometry(OPEN, ZERO, 0);
        moving.framing(Scene.Kind.FOLLOW, false);
        moving.course(0);
        for (int frame = 0; frame < 180; frame++) {
            double heading = frame < 60 ? 0 : frame < 120 ? 110 : -70;
            moving.course(heading);
            moving.follow(ZERO, frame * 16_666_667L, false, true);
            Pose view = moving.apply(plan.shot().sample(0), plan.shot().focus(0), 0, 1, false);
            assertNotNull(view);
            if (moving.fade() < 1) {
                double radians = Math.toRadians(heading);
                assertTrue(-Math.sin(radians) * view.position().x() + Math.cos(radians) * view.position().z() <= .05,
                    "A recovery or turn revealed a front-facing camera");
            }
            moving.coveredFrame();
        }
        assertEquals(0, moving.fade());
    }

    @Test void walkingDownTerracesRaisesTheCameraInsteadOfCuttingAtEachStep() {
        Geometry terraces = new Geometry() {
            private double floor(double z) { return Math.clamp(Math.ceil(6 - z / 2), 0, 6); }
            private boolean line(Vec a, Vec b, double margin) {
                int steps = Math.max(1, (int)Math.ceil(a.distance(b) * 100));
                for (int i = 0; i <= steps; i++) {
                    Vec p = a.lerp(b, (double)i / steps);
                    if (p.y() - margin <= floor(p.z() - margin)) return false;
                }
                return true;
            }
            public boolean clear(Vec a, Vec b, double margin) { return line(a, b, .25 + margin); }
            public boolean visible(Vec a, Vec b, double margin) { return line(a, b, margin); }
        };
        var moving = new MovingGeometry(terraces, new Vec(0, 7.5, -4), 0);
        moving.framing(Scene.Kind.FOLLOW, false);
        moving.course(0);
        Pose local = new Pose(new Vec(0, .6, -6.5), 0, 0), previous = null;
        for (int frame = 0; frame < 360; frame++) {
            double z = -4 + frame / 15.0;
            Vec rider = new Vec(0, Math.clamp(Math.ceil(6 - z / 2), 0, 6) + 1.5, z);
            moving.follow(rider, frame * 16_666_667L, false, true);
            Pose view = moving.apply(local, ZERO, 0, 1, false);
            assertNotNull(view);
            assertEquals(0, moving.fade(), "A clear hillside should not need repeated blackouts");
            assertTrue(terraces.visible(view.position(), rider));
            if (previous != null) assertTrue(terraces.clear(previous.position(), view.position()));
            previous = view;
        }
    }

    @Test void oneManualSessionCanChangeActivityWithoutLosingItsCaptureOwner() {
        var config = new CinematicConfig();
        var moving = new MovingGeometry(OPEN, ZERO, 0);
        moving.course(0);
        var session = new CinematicSession(ZERO, new Pose(new Vec(0, 1, -4), 0, 0), 0, config, moving, new Random(9), Scene.Kind.FOLLOW);
        long now = 0;
        for (Scene.Kind kind : new Scene.Kind[]{Scene.Kind.FOLLOW, Scene.Kind.RIDE, Scene.Kind.SWIM, Scene.Kind.FLIGHT, Scene.Kind.FISHING, Scene.Kind.FOLLOW}) {
            for (int i = 0; i < 200 && !session.settled(); i++) {
                now += 50_000_000L;
                session.tick(now); session.frame(now); session.coveredFrame();
            }
            assertTrue(session.settled());
            int take = session.take();
            session.beginCapture(now);
            session.activity(kind, kind == Scene.Kind.FISHING ? new Vec(0, 0, 8) : null);
            assertTrue(session.capturing());
            assertEquals(take, session.take());
            session.endCapture(now + 50_000_000L);
            now += 50_000_000L;
            session.activity(kind, kind == Scene.Kind.FISHING ? new Vec(0, 0, 8) : null);
            for (int i = 0; i < 200 && session.take() == take; i++) {
                now += 50_000_000L;
                session.tick(now); session.frame(now); session.coveredFrame();
                assertFalse(session.ended());
            }
            assertTrue(session.take() > take, "Activity framing never changed to " + kind);
        }
    }

    @Test void optionalSightlinePaddingDoesNotRejectARiderBesideABlockCorner() {
        Geometry corner = new Geometry() {
            private boolean line(Vec a, Vec b, double margin) {
                int steps = Math.max(1, (int)Math.ceil(a.distance(b) * 100));
                for (int i = 0; i <= steps; i++) {
                    Vec p = a.lerp(b, (double)i / steps);
                    if (p.x() >= 1 - margin && p.x() <= 2 + margin && p.y() >= 1 - margin && p.y() <= 2 + margin
                        && p.z() >= 1 - margin && p.z() <= 2 + margin) return false;
                }
                return true;
            }
            public boolean clear(Vec a, Vec b, double margin) { return line(a, b, .25 + margin); }
            public boolean visible(Vec a, Vec b, double margin) { return line(a, b, margin); }
        };
        Vec rider = new Vec(.75, 1.5, .75), camera = new Vec(.5, 1.5, -2);
        assertTrue(corner.clear(camera, camera));
        assertTrue(corner.visible(camera, rider));
        assertFalse(corner.visible(camera, rider, .3));
        var moving = new MovingGeometry(corner, rider, 0);
        moving.course(0);
        Pose view = moving.apply(Pose.lookingAt(camera.subtract(rider), ZERO), ZERO, 0, 1, false);
        assertNotNull(view, "Optional clearance around the rider must not end an otherwise clear travel view");
        assertTrue(corner.clear(view.position(), view.position()));
        assertTrue(corner.visible(view.position(), rider));
    }

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
            assertTrue(moving.follow(rider, frame * 50_000_000L, false, false));
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
        moving.follow(ZERO, 50_000_000, false, false);
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
            moving.follow(ZERO, frame * 50_000_000L, false, false);
            Pose next = moving.apply(local, ZERO, 0, 1, false);
            assertNotNull(next);
            assertTrue(next.position().distance(previous.position()) <= .101, "Widening snapped back to the original distance");
            previous = next;
        }
        assertEquals(0, moving.fade());
        assertEquals(original.position().x(), previous.position().x(), 1e-6);
        assertEquals(original.position().z(), previous.position().z(), 1e-6);
        assertFalse(moving.follow(new Vec(40, 0, 0), 5_000_000_000L, false, false), "Recovery excused a teleport");
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
            moving.follow(ZERO, now, false, false);
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
        assertTrue(moving.follow(new Vec(3, 0, 0), 100_000_000, false, false));
        assertNull(moving.apply(left, ZERO, 1, 1, false), "A black frame cannot excuse travel through a wall");
        assertNotNull(moving.apply(left, ZERO, 1, 2, false), "An explicit covered cut may change sides");
        assertFalse(moving.follow(new Vec(50, 0, 0), 200_000_000, false, false), "Covered cuts cannot excuse a teleport");
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
        assertTrue(moving.follow(new Vec(0, 0, 2), 1, false, false));
        assertNotNull(moving.apply(plan.shot().sample(0), plan.shot().focus(0), 0, 1, false),
            "Acceleration in open air must not immediately exhaust the sightline range");
    }

    @Test void screenshotsHoldTheWorldPoseAndResumeSmoothly() {
        var moving = new MovingGeometry(OPEN, ZERO, 0);
        Pose local = new Pose(new Vec(3, 2, -7), 0, 0);
        Pose before = moving.apply(local, ZERO, 0, 1, false);
        assertTrue(moving.follow(new Vec(4, 0, 0), 100_000_000, true, false));
        assertEquals(before, moving.apply(local, ZERO, 0, 1, true));
        assertTrue(moving.follow(new Vec(4, 0, 0), 110_000_000, false, false));
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
            assertTrue(moving.follow(ZERO, now, false, false));
            Pose next = moving.apply(local, ZERO, 0, 1, false);
            assertTrue(next.position().distance(previous.position()) < .08, "The following view must turn gradually");
            previous = next;
        }
        assertEquals(-170, Math.IEEEremainder(previous.yaw(), 360), .1);
        moving.course(10);
        assertTrue(moving.needsReframe(Scene.Kind.BOAT));
        assertFalse(moving.needsReframe(Scene.Kind.BOAT), "A reversal requests one cut, not a queue of cuts");
        assertTrue(moving.follow(ZERO, 3_020_000_000L, true, false));
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
        assertTrue(moving.follow(new Vec(1, 0, 0), 100_000_000L, true, false));
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
            assertTrue(moving.follow(ZERO, now, false, false));
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
