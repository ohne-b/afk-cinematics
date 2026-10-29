package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentDriveTest {
    private static final Geometry OPEN = new Geometry() {
        public boolean clear(Vec from, Vec to, double margin) { return true; }
        public boolean visible(Vec from, Vec to, double margin) { return from.distance(to) + 2 * margin <= 32; }
    };

    @Test void aerialDrivesKeepTheirAltitudeHeadingAndMeaningfulTravel() {
        Vec focus = new Vec(0, 0.35, 0), vantage = new Vec(0, 14.35, -22);
        var scene = new Scene(focus, vantage, Scene.Kind.AERIAL, 8);
        for (Shot.Type type : new Shot.Type[]{Shot.Type.FLY_THROUGH, Shot.Type.TRAVERSE, Shot.Type.FLYOVER}) {
            var config = new CinematicConfig();
            config.shots = EnumSet.of(type);
            Shot shot = new ShotSearch(scene, Pose.lookingAt(vantage, focus), 0, config, null, true, true, new Random(4)).next(OPEN);
            assertNotNull(shot, type.name());
            assertTrue(shot.start().distance(focus) > 20);
            assertTrue(shot.start().distance(shot.end()) >= 11);
            for (int sample = 0; sample <= 100; sample++) {
                Pose pose = shot.sample(shot.duration() * sample / 100);
                assertEquals(vantage.y(), pose.position().y(), 1e-9);
                assertEquals(shot.sample(0).yaw(), pose.yaw(), 1e-9);
                assertEquals(shot.sample(0).pitch(), pose.pitch(), 1e-9);
            }
        }
    }

    @Test void lowAndSubmergedDrivesDoNotBecomeAerialShots() {
        for (Scene.Kind kind : new Scene.Kind[]{Scene.Kind.GROUND, Scene.Kind.WATER}) {
            Vec focus = new Vec(0, 2, 0), vantage = new Vec(0, 2.5, -8);
            var config = new CinematicConfig();
            config.shots = EnumSet.of(Shot.Type.FLY_THROUGH, Shot.Type.TRAVERSE);
            Shot shot = new ShotSearch(new Scene(focus, vantage, kind, 8), Pose.lookingAt(vantage, focus),
                0, config, null, true, true, new Random(3)).next(OPEN);
            assertNotNull(shot);
            assertEquals(vantage.y(), shot.start().y());
            assertEquals(vantage.y(), shot.end().y());
        }
    }

    @Test void aerialViewsRetainFixedPositionWhenOnlyStillOrPanIsSelected() {
        for (Shot.Type type : new Shot.Type[]{Shot.Type.STILL, Shot.Type.PAN}) {
            Vec focus = new Vec(0, 0, 0), vantage = new Vec(0, 14, -22);
            var config = new CinematicConfig();
            config.shots = EnumSet.of(type);
            Shot shot = new ShotSearch(new Scene(focus, vantage, Scene.Kind.AERIAL, 8), Pose.lookingAt(vantage, focus),
                0, config, null, true, true, new Random(2)).next(OPEN);
            assertNotNull(shot);
            assertEquals(vantage, shot.start());
            assertEquals(vantage, shot.end());
        }
    }

    @Test void newSceneryDrivesDoNotTakeThePlayerOutOfFrame() {
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.FLY_THROUGH, Shot.Type.TRAVERSE);
        var player = new Scene(new Vec(0, 1, 0), new Vec(0, 1.6, 0), Scene.Kind.PLAYER, 0);
        assertTrue(ShotSearch.stylesFor(player, config).isEmpty());
        assertEquals(0, ShotSearch.playerAttempts(config));
    }

    @Test void repeatedSearchesVaryTheirTerrainProbeDirections() {
        var directions = new ArrayList<Double>();
        var geometry = new Geometry() {
            public boolean clear(Vec from, Vec to, double margin) { return true; }
            public boolean visible(Vec from, Vec to, double margin) { return true; }
            public Scene scenery(Vec anchor, Pose entry, double bearing, int probe) { directions.add(bearing); return null; }
        };
        var config = new CinematicConfig();
        config.subject = CinematicConfig.Subject.SCENERY;
        var random = new Random(9);
        for (int take = 0; take < 5; take++) {
            var planner = new ShotPlanner(new Vec(0, 1, 0), new Pose(new Vec(0, 1, 0), 0, 0),
                new Pose(new Vec(0, 1, 0), 0, 0), 0, config, null, List.of(), 0, null, random);
            planner.next(geometry);
        }
        assertEquals(5, directions.stream().distinct().count());
    }
}
