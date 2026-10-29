package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class SceneryTest {
    private static final Vec PLAYER = new Vec(0, 1, 0);
    private static final Pose ENTRY = new Pose(new Vec(0, 1.6, 0), 0, 0);
    private static final Scene SURFACE = new Scene(new Vec(8, 20, 0), new Vec(8, 22, -8), true, 3);

    @Test void crampedPlayerFallsBackToSceneryWithoutTravellingThroughWalls() {
        var session = session(CinematicConfig.Subject.MIXED, cavern(new boolean[]{false}));
        Pose previous = ENTRY;
        double opacity = 0;
        int cuts = 0;
        for (int tick = 0; tick < 200; tick++) {
            long now = tick * 50_000_000L;
            session.tick(now);
            Pose pose = session.frame(now);
            assertFalse(session.ended());
            if (pose == null) continue;
            if (pose.position().distance(previous.position()) > 2) {
                assertEquals(1, opacity, "The last old-view frame must be covered");
                assertEquals(1, session.fade(), "The first new-view frame must be covered");
                cuts++;
            }
            assertTrue(pose.position().distance(ENTRY.position()) < 0.01 || pose.position().y() > 10,
                "Camera crossed the cave ceiling instead of cutting");
            assertTrue(Scene.inRange(pose.position(), PLAYER));
            previous = pose;
            opacity = session.fade();
        }
        assertEquals(1, cuts);
        assertTrue(session.scenery());
        assertEquals(0, session.fade());
    }

    @Test void destinationIsRevalidatedBeforeItIsRevealed() {
        boolean[] obstructed = {false};
        var session = session(CinematicConfig.Subject.SCENERY, cavern(obstructed));
        long now = 0;
        while (session.fade() != 1 && now < 10_000_000_000L) {
            session.tick(now);
            session.frame(now);
            now += 50_000_000L;
        }
        assertEquals(1, session.fade());
        assertEquals(ENTRY, session.current());
        obstructed[0] = true;
        assertNull(session.frame(now));
        assertTrue(session.ended());
        assertEquals(ENTRY, session.current());
    }

    @Test void subjectModesDoNotSilentlyChooseTheOtherSubject() {
        Geometry playerOnly = new Geometry() {
            public boolean clear(Vec a, Vec b) { return true; }
            public boolean visible(Vec a, Vec b) { return true; }
            public Scene scenery(Vec anchor, Pose original, double bearing, int probe) {
                fail("Player mode queried scenery"); return null;
            }
        };
        var player = session(CinematicConfig.Subject.PLAYER, playerOnly);
        player.tick(0);
        assertNotNull(player.frame(0));
        assertFalse(player.scenery());
        var scenery = session(CinematicConfig.Subject.SCENERY, new Geometry() {
            public boolean clear(Vec a, Vec b) { return true; }
            public boolean visible(Vec a, Vec b) { return true; }
        });
        for (int tick = 0; tick < 30; tick++) scenery.tick(tick * 50_000_000L);
        assertTrue(scenery.ended());
        assertFalse(scenery.showing());
    }

    @Test void mixedModeAlternatesSubjectsAndStaysWithinThePlayerRange() {
        Geometry open = new Geometry() {
            public boolean clear(Vec a, Vec b) { return true; }
            public boolean visible(Vec a, Vec b) { return true; }
            public Scene scenery(Vec anchor, Pose original, double bearing, int probe) { return probe == 0 ? SURFACE : null; }
        };
        var session = session(CinematicConfig.Subject.MIXED, open);
        boolean sawScenery = false, returned = false;
        for (int tick = 0; tick < 1100; tick++) {
            long now = tick * 50_000_000L;
            session.tick(now);
            Pose pose = session.frame(now);
            assertNotNull(pose);
            assertTrue(Scene.inRange(pose.position(), PLAYER));
            if (session.scenery()) sawScenery = true;
            else if (sawScenery) returned = true;
        }
        assertTrue(sawScenery);
        assertTrue(returned);
    }

    @Test void invalidDistantCandidatesHaveAFiniteSearchBudget() {
        int[] probes = {0};
        var session = session(CinematicConfig.Subject.SCENERY, new Geometry() {
            public boolean clear(Vec a, Vec b) { return true; }
            public boolean visible(Vec a, Vec b) { return true; }
            public Scene scenery(Vec anchor, Pose original, double bearing, int probe) {
                probes[0]++;
                return new Scene(new Vec(200, 200, 0), new Vec(200, 202, -8), true, 100);
            }
        });
        for (int tick = 0; tick < 30; tick++) session.tick(tick * 50_000_000L);
        assertTrue(session.ended());
        assertEquals(24, probes[0]);
    }

    private CinematicSession session(CinematicConfig.Subject subject, Geometry geometry) {
        var config = new CinematicConfig();
        config.subject = subject;
        config.pace = CinematicConfig.Pace.SHORT;
        config.shots = EnumSet.of(Shot.Type.ARC, Shot.Type.PAN, Shot.Type.STILL);
        return new CinematicSession(PLAYER, ENTRY, 0, config, geometry, new Random(4));
    }

    private Geometry cavern(boolean[] obstructed) {
        return new Geometry() {
            public boolean clear(Vec a, Vec b) {
                return a.distance(ENTRY.position()) < 0.01 && b.distance(ENTRY.position()) < 0.01
                    || !obstructed[0] && a.y() > 10 && b.y() > 10;
            }
            public boolean visible(Vec a, Vec b) { return a.y() > 10; }
            public Scene scenery(Vec anchor, Pose original, double bearing, int probe) { return probe == 0 ? SURFACE : null; }
        };
    }
}
