package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentTest {
    private static final Vec ANCHOR = new Vec(0, 1, 0);
    private static final Pose ENTRY = new Pose(new Vec(0, 2, 0), 0, 0);
    private static final Scene WATER = new Scene(new Vec(8, -3, 0), new Vec(8, -1.5, -6), Scene.Kind.WATER, 5);
    private static final Scene LAND = new Scene(new Vec(-8, 1, 0), new Vec(-8, 3, -6), Scene.Kind.GROUND, 5);

    @Test void coveredCutsUseTheEnvironmentOfEachView() {
        boolean[] chooseWater = {true}, present = {true};
        var session = session(environment(chooseWater, present));
        Pose previous = ENTRY;
        double opacity = 0;
        int cuts = 0;
        boolean returned = false;
        for (int tick = 0; tick < 180; tick++) {
            long now = tick * 50_000_000L;
            session.tick(now);
            Pose pose = session.frame(now);
            assertFalse(session.ended(), "The old and destination views used the same environment");
            if (pose == null) continue;
            if (pose.position().distance(previous.position()) > 2) {
                assertEquals(1, opacity);
                assertEquals(1, session.fade());
                cuts++;
            }
            if (session.underwater() && session.settled() && chooseWater[0]) {
                chooseWater[0] = false;
                session.nextShot();
            }
            if (!chooseWater[0] && !session.underwater() && session.settled()) returned = true;
            previous = pose;
            opacity = session.fade();
            session.coveredFrame();
        }
        assertTrue(returned);
        assertEquals(2, cuts);
    }

    @Test void drainedWaterIsRejectedBeforeTheCoveredHandoff() {
        boolean[] present = {true};
        var session = session(environment(new boolean[]{true}, present));
        long now = 0;
        while (session.fade() != 1 && now < 5_000_000_000L) {
            session.tick(now);
            session.frame(now);
            now += 50_000_000L;
        }
        assertEquals(1, session.fade());
        assertEquals(ENTRY, session.current());
        present[0] = false;
        session.coveredFrame();
        assertNull(session.frame(now));
        assertTrue(session.ended());
        assertEquals(ENTRY, session.current());
    }

    @Test void groundViewsKeepTheirLowAngleAndRespectTheSelectedStyles() {
        Geometry open = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
        };
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.FLYOVER, Shot.Type.OVERHEAD, Shot.Type.CRANE, Shot.Type.REVEAL);
        var search = new ShotSearch(LAND, ENTRY, 0, config, null, true, true, new Random(1));
        assertTrue(search.exhausted());
        config.shots.add(Shot.Type.DRIFT);
        var shot = new ShotSearch(LAND, ENTRY, 0, config, null, true, true, new Random(1)).next(open);
        assertNotNull(shot);
        assertEquals(Shot.Type.DRIFT, shot.type());
        assertEquals(LAND.vantage().y(), shot.start().y());
        assertEquals(shot.start().y(), shot.end().y());
    }

    @Test void unsupportedGroundViewsDoNotHideAUsableLandscape() {
        var config = new CinematicConfig();
        config.subject = CinematicConfig.Subject.SCENERY;
        config.shots = EnumSet.of(Shot.Type.FLYOVER);
        var planner = new ShotPlanner(ANCHOR, ENTRY, ENTRY, 0, config, new ShotHistory(), new Random(1));
        Geometry geometry = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
            public Scene scenery(Vec anchor, Pose original, double bearing, int probe) {
                if (probe < 4) return new Scene(new Vec(-12 + probe * 8, 1, 0),
                    new Vec(-12 + probe * 8, 1.6, -8), Scene.Kind.GROUND, 10);
                return probe == 4 ? new Scene(new Vec(0, 1, 8), new Vec(0, 3, 0), Scene.Kind.LANDSCAPE, 1) : null;
            }
        };
        ShotPlanner.Plan found = null;
        for (int step = 0; step < 150 && found == null && !planner.exhausted(); step++) found = planner.next(geometry);
        assertNotNull(found, "Unsupported ground views consumed the complete candidate budget");
        assertEquals(Scene.Kind.LANDSCAPE, found.scene().kind());
        assertEquals(Shot.Type.FLYOVER, found.shot().type());
    }

    private static CinematicSession session(Geometry geometry) {
        var config = new CinematicConfig();
        config.subject = CinematicConfig.Subject.SCENERY;
        config.shots = EnumSet.of(Shot.Type.STILL);
        return new CinematicSession(ANCHOR, ENTRY, 0, config, geometry, new Random(1));
    }

    private static Geometry environment(boolean[] chooseWater, boolean[] present) {
        Geometry wet = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return present[0] && Math.max(a.y(), b.y()) + margin < 0; }
            public boolean visible(Vec a, Vec b, double margin) { return present[0]; }
        };
        return new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return Math.min(a.y(), b.y()) - margin > 0; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
            public Geometry forScene(Scene scene) { return scene != null && scene.kind() == Scene.Kind.WATER ? wet : this; }
            public Scene scenery(Vec anchor, Pose original, double bearing, int probe) {
                return probe == 0 ? chooseWater[0] ? WATER : LAND : null;
            }
        };
    }
}
