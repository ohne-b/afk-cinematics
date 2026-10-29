package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ShotMotionTest {
    private static final Vec SUBJECT = new Vec(0, 1, 0);
    private static final Geometry OPEN = new Geometry() {
        public boolean clear(Vec from, Vec to, double margin) { return true; }
        public boolean visible(Vec from, Vec to, double margin) { return true; }
    };

    @Test void motionHasShortSoftRampsAndASteadyMiddle() {
        var shot = new Shot(Shot.Type.DRIFT, new Vec(0, 2, -8), new Vec(1.5, 2, -8),
            new Vec(3, 2, -8), new Vec(0, 1, 0), new Vec(3, 1, 0), 24);
        assertTrue(shot.sample(4.8).position().x() > 0.45, "The opening fifth barely travels");
        double step = shot.sample(12.5).position().distance(shot.sample(12).position());
        for (double seconds = 2; seconds <= 21.5; seconds += 0.5)
            assertEquals(step, shot.sample(seconds + 0.5).position().distance(shot.sample(seconds).position()), 1e-9);
        assertTrue(shot.sample(0.5).position().distance(shot.start()) < step * 0.15);
        assertTrue(shot.sample(23.5).position().distance(shot.end()) < step * 0.15);
        for (int frame = 0; frame <= 240; frame++) {
            double seconds = frame / 10.0;
            assertEquals(shot.sample(seconds).position().x(), shot.focus(seconds).x(), 1e-12,
                "Camera and focus left their shared path parameter");
        }
        assertEquals(shot.sample(0), shot.sample(-1));
        assertEquals(shot.sample(24), shot.sample(25));
    }

    @Test void dollyAtTheOuterLimitTravelsInwardInsteadOfBecomingAStill() {
        var scene = new Scene(SUBJECT, SUBJECT.add(new Vec(0, 2, -12)), Scene.Kind.LANDSCAPE, 1);
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.DOLLY);
        for (int seed = 0; seed < 8; seed++) {
            var shot = new ShotSearch(scene, Pose.lookingAt(scene.vantage(), SUBJECT), 0, config,
                null, true, true, new Random(seed)).next(OPEN);
            assertNotNull(shot);
            assertTrue(shot.start().distance(shot.end()) > 2, "Outer-limit dolly lost its movement");
            assertTrue(shot.end().distance(SUBJECT) < shot.start().distance(SUBJECT));
        }
    }

    @Test void lateralSceneryTravelCreatesParallaxAndReducedMotionStaysSmaller() {
        var scene = new Scene(SUBJECT, SUBJECT.add(new Vec(0, 2, -8)), Scene.Kind.LANDSCAPE, 1);
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.DRIFT);
        var pose = Pose.lookingAt(scene.vantage(), SUBJECT);
        Shot gentle = new ShotSearch(scene, pose, 0, config, null, true, true, new Random(2)).next(OPEN);
        config.motion = CinematicConfig.Motion.REDUCED;
        Shot reduced = new ShotSearch(scene, pose, 0, config, null, true, true, new Random(2)).next(OPEN);
        assertNotNull(gentle);
        assertNotNull(reduced);
        assertTrue(gentle.start().distance(gentle.end()) >= 2.5);
        assertTrue(reduced.start().distance(reduced.end()) < gentle.start().distance(gentle.end()) * 0.4);
    }

    @Test void cameraDrivesKeepMovingInOneDirectionAndRespectReducedMotion() {
        var scene = new Scene(SUBJECT, SUBJECT.add(new Vec(0, 2, -8)), Scene.Kind.LANDSCAPE, 1);
        var config = new CinematicConfig();
        for (Shot.Type type : new Shot.Type[]{Shot.Type.FLYOVER, Shot.Type.LOW_PASS, Shot.Type.REVEAL}) {
            config.shots = EnumSet.of(type);
            config.motion = CinematicConfig.Motion.GENTLE;
            var entry = Pose.lookingAt(scene.vantage(), SUBJECT);
            Shot shot = new ShotSearch(scene, entry, 0, config, null, true, true, new Random(2)).next(OPEN);
            assertNotNull(shot);
            Vec travel = shot.end().subtract(shot.start());
            assertTrue(travel.length() > 4, type + " did not create a substantial camera drive");
            for (int sample = 2; sample < 98; sample++) {
                Vec step = shot.sample(shot.duration() * sample / 100).position()
                    .subtract(shot.sample(shot.duration() * (sample - 1) / 100).position());
                assertTrue(step.x() * travel.x() + step.y() * travel.y() + step.z() * travel.z() > 0,
                    type + " stopped or reversed within the take");
                if (type == Shot.Type.FLYOVER) {
                    assertEquals(shot.sample(0).yaw(), shot.sample(shot.duration() * sample / 100).yaw(), 1e-9);
                    assertEquals(shot.sample(0).pitch(), shot.sample(shot.duration() * sample / 100).pitch(), 1e-9);
                }
            }
            if (type == Shot.Type.LOW_PASS) assertEquals(SUBJECT.y() + 0.25, shot.start().y(), 1e-9);
            if (type == Shot.Type.REVEAL) {
                assertTrue(shot.end().y() - shot.start().y() > 3);
                assertTrue(shot.end().distance(SUBJECT) > shot.start().distance(SUBJECT));
            }
            config.motion = CinematicConfig.Motion.REDUCED;
            Shot reduced = new ShotSearch(scene, entry, 0, config, null, true, true, new Random(2)).next(OPEN);
            assertTrue(reduced.start().distance(reduced.end()) < travel.length() * 0.4);
        }
    }
}
