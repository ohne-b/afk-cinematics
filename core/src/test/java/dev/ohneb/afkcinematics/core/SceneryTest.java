package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.ArrayList;
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
            session.coveredFrame();
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
        session.coveredFrame();
        assertNull(session.frame(now));
        assertTrue(session.ended());
        assertEquals(ENTRY, session.current());
    }

    @Test void elevatedStillAndPanoramaViewsKeepTheirPosition() {
        var elevated = new Scene(PLAYER, new Vec(8, 14.15, 0), true, 1);
        Geometry open = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
        };
        Geometry ledge = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return a.y() >= 14 && b.y() >= 14; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
        };
        for (Shot.Type type : new Shot.Type[]{Shot.Type.STILL, Shot.Type.PAN}) {
            var config = new CinematicConfig();
            config.shots = EnumSet.of(type);
            Shot shot = new ShotSearch(elevated, ENTRY, 0, config, null, true, true, new Random(3)).next(open);
            assertNotNull(shot);
            assertEquals(shot.start(), shot.control(), type + " changed position at an elevated viewpoint");
            assertEquals(shot.start(), shot.end(), type + " drifted downward");
            assertNull(new ShotSearch(elevated, ENTRY, 0, config, null, true, true, new Random(3)).next(ledge),
                "Clamping must not place the camera inside a ledge");
        }
    }

    @Test void subjectModesDoNotSilentlyChooseTheOtherSubject() {
        Geometry playerOnly = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
            public Scene scenery(Vec anchor, Pose original, double bearing, int probe) {
                fail("Player mode queried scenery"); return null;
            }
        };
        var player = session(CinematicConfig.Subject.PLAYER, playerOnly);
        player.tick(0);
        assertNotNull(player.frame(0));
        assertFalse(player.scenery());
        var scenery = session(CinematicConfig.Subject.SCENERY, new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
        });
        for (int tick = 0; tick < 30; tick++) scenery.tick(tick * 50_000_000L);
        assertTrue(scenery.ended());
        assertFalse(scenery.showing());
    }

    @Test void mixedModeAlternatesSubjectsAndStaysWithinThePlayerRange() {
        Geometry open = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
            public Scene scenery(Vec anchor, Pose original, double bearing, int probe) { return probe == 0 ? SURFACE : null; }
        };
        var session = session(CinematicConfig.Subject.MIXED, open);
        boolean sawScenery = false, returned = false;
        for (int tick = 0; tick < 1100; tick++) {
            long now = tick * 50_000_000L;
            session.tick(now);
            Pose pose = session.frame(now);
            session.coveredFrame();
            assertNotNull(pose);
            assertTrue(Scene.inRange(pose.position(), PLAYER));
            if (session.scenery()) sawScenery = true;
            else if (sawScenery) returned = true;
        }
        assertTrue(sawScenery);
        assertTrue(returned);
    }

    @Test void extraCameraUpdatesCannotAdvanceACutWithoutCoveredGuiFrames() {
        var session = session(CinematicConfig.Subject.SCENERY, cavern(new boolean[]{false}));
        long now = 0;
        while (session.fade() != 1 && now < 10_000_000_000L) {
            session.tick(now);
            session.frame(now);
            now += 50_000_000L;
        }
        assertEquals(1, session.fade());
        for (int frame = 0; frame < 30; frame++) {
            assertEquals(ENTRY, session.frame(now));
            now += 50_000_000L;
        }
        session.coveredFrame();
        Pose destination = session.frame(now);
        assertNotEquals(ENTRY, destination);
        for (int frame = 0; frame < 30; frame++) {
            now += 50_000_000L;
            assertEquals(destination, session.frame(now));
            assertEquals(1, session.fade());
        }
        session.coveredFrame();
        for (int frame = 0; frame < 20; frame++) session.frame(now += 50_000_000L);
        assertEquals(0, session.fade());
        assertFalse(session.ended());
    }

    @Test void invalidDistantCandidatesHaveAFiniteSearchBudget() {
        int[] probes = {0};
        var session = session(CinematicConfig.Subject.SCENERY, new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
            public Scene scenery(Vec anchor, Pose original, double bearing, int probe) {
                probes[0]++;
                return new Scene(new Vec(200, 200, 0), new Vec(200, 202, -8), true, 100);
            }
        });
        for (int tick = 0; tick < 30; tick++) session.tick(tick * 50_000_000L);
        assertTrue(session.ended());
        assertEquals(Scene.PROBES, probes[0]);
    }

    @Test void mixedModeRemembersTheLastSceneryAcrossPlayerShots() {
        Scene second = new Scene(new Vec(-8, 20, 0), new Vec(-8, 22, -8), true, 2);
        Geometry open = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
            public Scene scenery(Vec anchor, Pose original, double bearing, int probe) {
                return probe == 0 ? SURFACE : probe == 1 ? second : null;
            }
        };
        var session = session(CinematicConfig.Subject.MIXED, open);
        var views = new ArrayList<Double>();
        boolean wasScenery = false;
        for (int tick = 0; tick < 1600; tick++) {
            long now = tick * 50_000_000L;
            session.tick(now);
            Pose pose = session.frame(now);
            assertNotNull(pose);
            if (session.scenery() && !wasScenery) views.add(pose.position().x());
            wasScenery = session.scenery();
            session.coveredFrame();
        }
        assertTrue(views.size() >= 2);
        assertTrue(views.get(0) > 0 && views.get(1) < 0, "Player shots erased scenery variety");
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
            public boolean clear(Vec a, Vec b, double margin) {
                return a.distance(ENTRY.position()) < 0.01 && b.distance(ENTRY.position()) < 0.01
                    || !obstructed[0] && a.y() > 10 && b.y() > 10;
            }
            public boolean visible(Vec a, Vec b, double margin) { return a.y() > 10; }
            public Scene scenery(Vec anchor, Pose original, double bearing, int probe) { return probe == 0 ? SURFACE : null; }
        };
    }
}
