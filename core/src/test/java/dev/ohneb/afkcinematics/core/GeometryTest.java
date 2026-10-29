package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class GeometryTest {
    @Test void wholeShotRangeAndEntryDistanceStayBounded() {
        Geometry geometry = surface();
        Vec focus = new Vec(0, 1, 0), start = new Vec(0, 1, -15);
        Shot excursion = new Shot(Shot.Type.DOLLY, start, new Vec(0, 1, -20), start, focus, focus, 20);
        assertTrue(geometry.visible(start, focus));
        assertFalse(geometry.valid(excursion, start));
        Shot still = new Shot(Shot.Type.STILL, start, start, start, focus, focus, 20);
        assertTrue(geometry.valid(still, start));
        assertFalse(geometry.valid(still, start.add(new Vec(17, 0, 0))));
    }

    @Test void clearSurfaceSceneryStillAcceptsEveryStyle() {
        for (int distance : new int[]{8, 12}) for (Shot.Type type : Shot.Type.values()) {
            Vec focus = new Vec(0, 0.35, 0), vantage = new Vec(0, 2.85, -distance);
            Scene scene = new Scene(focus, vantage, Scene.Kind.LANDSCAPE, 1);
            var config = new CinematicConfig();
            config.shots = EnumSet.of(type);
            Shot shot = new ShotSearch(scene, Pose.lookingAt(vantage, focus), 0, config,
                null, true, true, new Random(2)).next(surface());
            assertNotNull(shot, type + " at distance " + distance);
        }
    }

    @Test void aCraneAboveTheOldThresholdRisesWhenThereIsNoRoomToDescend() {
        for (int distance : new int[]{8, 12}) {
            Vec focus = new Vec(0, 0.35, 0), vantage = new Vec(0, 3.35, -distance);
            var config = new CinematicConfig();
            config.shots = EnumSet.of(Shot.Type.CRANE);
            Shot shot = new ShotSearch(new Scene(focus, vantage, Scene.Kind.LANDSCAPE, 1), Pose.lookingAt(vantage, focus),
                0, config, null, true, true, new Random(2)).next(surface());
            assertNotNull(shot, "A safe upward crane was lost at distance " + distance);
            assertTrue(shot.end().y() > shot.start().y());
        }
    }

    @Test void aReducedCraneCanDescendBelowALowCeiling() {
        Geometry room = new Geometry() {
            public boolean clear(Vec from, Vec to, double margin) {
                return surface().clear(from, to, margin) && Math.max(from.y(), to.y()) + margin < 4;
            }
            public boolean visible(Vec from, Vec to, double margin) {
                return surface().visible(from, to, margin) && Math.max(from.y(), to.y()) + margin < 4;
            }
        };
        for (int distance : new int[]{8, 12}) {
            Vec focus = new Vec(0, 0.35, 0), vantage = new Vec(0, 3.35, -distance);
            var config = new CinematicConfig();
            config.shots = EnumSet.of(Shot.Type.CRANE);
            config.motion = CinematicConfig.Motion.REDUCED;
            Shot shot = new ShotSearch(new Scene(focus, vantage, Scene.Kind.LANDSCAPE, 1), Pose.lookingAt(vantage, focus),
                0, config, null, true, true, new Random(2)).next(room);
            assertNotNull(shot, "A short downward crane was lost at distance " + distance);
            assertEquals(2.3, shot.end().y(), 0.00001);
        }
    }

    @Test void entryVisibilityCoversThePathBetweenClearEndpoints() {
        Vec focus = new Vec(0, 0, 0), start = new Vec(1, 0, -4), entry = new Vec(-1, 0, -4);
        Geometry geometry = obstacle(new Vec(0.013, 0, -2), false);
        Shot still = new Shot(Shot.Type.STILL, start, start, start, focus, focus, 20);
        assertTrue(geometry.visible(entry, focus));
        assertTrue(geometry.valid(still, start));
        assertFalse(geometry.valid(still, entry));
    }

    @Test void stationaryPanCoversTheChangingFocusBetweenSamples() {
        Vec camera = new Vec(0, 0, -4);
        Shot pan = new Shot(Shot.Type.PAN, camera, camera, camera,
            new Vec(-1, 0, 0), new Vec(1, 0, 0), 20);
        Geometry geometry = obstacle(new Vec(0.013, 0, -2), false);
        for (int sample = 0; sample <= 16; sample++) {
            assertTrue(geometry.visible(camera, pan.focus(pan.duration() * sample / 16)));
        }
        assertFalse(geometry.valid(pan, camera));
    }

    @Test void curvedCameraVolumeIncludesTheBulgeAwayFromItsChord() {
        Shot arc = new Shot(Shot.Type.ARC, new Vec(-2, 0, -4), new Vec(0, 2, -4),
            new Vec(2, 0, -4), new Vec(0, 0, 0), new Vec(0, 0, 0), 20);
        Vec obstruction = arc.sample(arc.duration() * 0.514).position();
        Geometry geometry = obstacle(obstruction, true);
        assertFalse(geometry.valid(arc, arc.start()));
        assertTrue(obstacle(obstruction.add(new Vec(0, 0, 0.1)), true).valid(arc, arc.start()));
    }

    private static Geometry obstacle(Vec obstruction, boolean camera) {
        return new Geometry() {
            public boolean clear(Vec from, Vec to, double margin) {
                return !camera || distance(obstruction, from, to) > margin + 0.00001;
            }
            public boolean visible(Vec from, Vec to, double margin) {
                return camera || distance(obstruction, from, to) > margin + 0.00001;
            }
        };
    }

    private static Geometry surface() {
        return new Geometry() {
            public boolean clear(Vec from, Vec to, double margin) { return Math.min(from.y(), to.y()) - margin > 0.25; }
            public boolean visible(Vec from, Vec to, double margin) {
                return Math.min(from.y(), to.y()) - margin > 0 && from.distance(to) + 2 * margin <= 16;
            }
        };
    }

    private static double distance(Vec point, Vec from, Vec to) {
        Vec line = to.subtract(from), offset = point.subtract(from);
        double lengthSquared = line.x() * line.x() + line.y() * line.y() + line.z() * line.z();
        if (lengthSquared == 0) return point.distance(from);
        double along = (offset.x() * line.x() + offset.y() * line.y() + offset.z() * line.z()) / lengthSquared;
        return point.distance(from.lerp(to, Math.clamp(along, 0, 1)));
    }
}
