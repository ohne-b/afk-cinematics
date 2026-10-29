package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeometryTest {
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

    private static double distance(Vec point, Vec from, Vec to) {
        Vec line = to.subtract(from), offset = point.subtract(from);
        double lengthSquared = line.x() * line.x() + line.y() * line.y() + line.z() * line.z();
        if (lengthSquared == 0) return point.distance(from);
        double along = (offset.x() * line.x() + offset.y() * line.y() + offset.z() * line.z()) / lengthSquared;
        return point.distance(from.lerp(to, Math.clamp(along, 0, 1)));
    }
}
