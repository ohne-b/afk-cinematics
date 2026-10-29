package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ShotOptionsTest {
    private static final Vec SUBJECT = new Vec(0, 1, 0);
    private static final Pose ENTRY = new Pose(new Vec(0, 1.6, 0), 0, 0);
    private static final Geometry OPEN = new Geometry() {
        public boolean clear(Vec from, Vec to) { return true; }
        public boolean visible(Vec from, Vec target) { return true; }
    };

    @Test void eachSelectedStyleHasItsOwnCameraMotion() {
        for (Shot.Type type : Shot.Type.values()) {
            var config = new CinematicConfig();
            config.shots = EnumSet.of(type);
            Shot shot = new ShotSearch(SUBJECT, ENTRY, 0, config, null, true, new Random(2)).next(OPEN);
            assertNotNull(shot, type.name());
            assertEquals(type, shot.type());
            Pose first = shot.sample(0), last = shot.sample(shot.duration());
            if (type == Shot.Type.PAN) {
                assertEquals(first.position(), last.position());
                assertTrue(Math.abs(last.yaw() - first.yaw()) > 10);
            } else if (type == Shot.Type.STILL) assertEquals(first, last);
            else assertTrue(first.position().distance(last.position()) > 0.1, type.name());
            if (type == Shot.Type.CRANE) assertTrue(Math.abs(first.position().y() - last.position().y()) > 1);
            if (type == Shot.Type.OVERHEAD) assertTrue(first.pitch() > 60 && last.pitch() > 60);
        }
    }

    @Test void changingFromAPanPreservesTheExactStartingView() {
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.PAN);
        Shot pan = new ShotSearch(SUBJECT, ENTRY, 0, config, null, true, new Random(2)).next(OPEN);
        Pose previous = pan.sample(pan.duration());
        for (Shot.Type type : Shot.Type.values()) {
            config.shots = EnumSet.of(type);
            Shot next = new ShotSearch(SUBJECT, previous, 0, config, pan.type(), false, new Random(3)).next(OPEN);
            assertNotNull(next);
            Pose start = next.sample(0);
            assertEquals(previous.position(), start.position());
            assertEquals(0, Math.IEEEremainder(start.yaw() - previous.yaw(), 360), 1e-9);
            assertEquals(previous.pitch(), start.pitch(), 1e-9);
            if (type == Shot.Type.PAN) assertTrue(Math.abs(Math.IEEEremainder(
                next.sample(next.duration()).yaw() - start.yaw(), 360)) > 1, "A consecutive panorama must keep turning");
        }
    }

    @Test void aLowCeilingFallsBackOnlyToAnEnabledStyle() {
        Geometry low = new Geometry() {
            public boolean clear(Vec from, Vec to) { return from.y() <= 2.5 && to.y() <= 2.5; }
            public boolean visible(Vec from, Vec target) { return true; }
        };
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.OVERHEAD, Shot.Type.DRIFT);
        Shot shot = find(new ShotSearch(SUBJECT, ENTRY, 0, config, null, true, new Random(2)), low);
        assertNotNull(shot);
        assertEquals(Shot.Type.DRIFT, shot.type());
        config.shots = EnumSet.of(Shot.Type.OVERHEAD);
        assertNull(find(new ShotSearch(SUBJECT, ENTRY, 0, config, null, true, new Random(2)), low));
    }

    @Test void visibilityFollowsTheWholePan() {
        Geometry narrow = new Geometry() {
            public boolean clear(Vec from, Vec to) { return true; }
            public boolean visible(Vec from, Vec target) { return target.distance(SUBJECT) < 0.5; }
        };
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.PAN);
        assertNull(find(new ShotSearch(SUBJECT, ENTRY, 0, config, null, true, new Random(2)), narrow));
    }

    @Test void pacingControlsDurationAndAvailableStylesAvoidImmediateRepeats() {
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.ARC, Shot.Type.DOLLY);
        for (var pace : CinematicConfig.Pace.values()) {
            config.pace = pace;
            Shot shot = new ShotSearch(SUBJECT, ENTRY, 0, config, Shot.Type.ARC, true, new Random(2)).next(OPEN);
            assertEquals(Shot.Type.DOLLY, shot.type());
            assertTrue(shot.duration() >= pace.minimumSeconds && shot.duration() < pace.maximumSeconds);
        }
    }

    private static Shot find(ShotSearch search, Geometry geometry) {
        for (int attempt = 0; attempt < 100 && !search.exhausted(); attempt++) {
            Shot shot = search.next(geometry);
            if (shot != null) return shot;
        }
        assertTrue(search.exhausted());
        return null;
    }
}
