package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ShotOptionsTest {
    private static final Vec SUBJECT = new Vec(0, 1, 0);
    private static final Pose ENTRY = new Pose(new Vec(0, 1.6, 0), 0, 0);
    private static final Geometry OPEN = new Geometry() {
        public boolean clear(Vec from, Vec to, double margin) { return true; }
        public boolean visible(Vec from, Vec target, double margin) { return true; }
    };

    @Test void eachSelectedStyleHasItsOwnCameraMotion() {
        for (Shot.Type type : Shot.Type.values()) {
            var config = new CinematicConfig();
            config.shots = EnumSet.of(type);
            Shot shot = new ShotSearch(new Scene(SUBJECT, ENTRY.position(), false, 0), ENTRY, 0, config, null, true, false, new Random(2)).next(OPEN);
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
        Shot pan = new ShotSearch(new Scene(SUBJECT, ENTRY.position(), false, 0), ENTRY, 0, config, null, true, false, new Random(2)).next(OPEN);
        Pose previous = pan.sample(pan.duration());
        for (Shot.Type type : Shot.Type.values()) {
            config.shots = EnumSet.of(type);
            Shot next = new ShotSearch(new Scene(SUBJECT, ENTRY.position(), false, 0), previous, 0, config, pan.type(), false, false, new Random(3)).next(OPEN);
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
            public boolean clear(Vec from, Vec to, double margin) { return Math.max(from.y(), to.y()) + margin <= 2.5; }
            public boolean visible(Vec from, Vec target, double margin) { return true; }
        };
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.OVERHEAD, Shot.Type.DRIFT);
        Shot shot = find(new ShotSearch(new Scene(SUBJECT, ENTRY.position(), false, 0), ENTRY, 0, config, null, true, false, new Random(2)), low);
        assertNotNull(shot);
        assertEquals(Shot.Type.DRIFT, shot.type());
        config.shots = EnumSet.of(Shot.Type.OVERHEAD);
        assertNull(find(new ShotSearch(new Scene(SUBJECT, ENTRY.position(), false, 0), ENTRY, 0, config, null, true, false, new Random(2)), low));
    }

    @Test void visibilityFollowsTheWholePan() {
        Geometry narrow = new Geometry() {
            public boolean clear(Vec from, Vec to, double margin) { return true; }
            public boolean visible(Vec from, Vec target, double margin) { return target.distance(SUBJECT) + margin < 0.5; }
        };
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.PAN);
        assertNull(find(new ShotSearch(new Scene(SUBJECT, ENTRY.position(), false, 0), ENTRY, 0, config, null, true, false, new Random(2)), narrow));
    }

    @Test void pacingControlsDurationAndAvailableStylesAvoidImmediateRepeats() {
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.ARC, Shot.Type.DOLLY);
        for (var pace : CinematicConfig.Pace.values()) {
            config.pace = pace;
            Shot shot = new ShotSearch(new Scene(SUBJECT, ENTRY.position(), false, 0), ENTRY, 0, config, Shot.Type.ARC, true, false, new Random(2)).next(OPEN);
            assertEquals(Shot.Type.DOLLY, shot.type());
            assertTrue(shot.duration() >= pace.minimumSeconds && shot.duration() < pace.maximumSeconds);
        }
    }

    @Test void playerCoverageIncludesFrontBackAndBothSidesBeforeFallbacks() {
        var views = new java.util.HashSet<Integer>();
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.STILL);
        var random = new Random(4);
        for (int seed = 0; seed < 64; seed++) {
            Shot shot = new ShotSearch(new Scene(SUBJECT, ENTRY.position(), false, 0), ENTRY, 0,
                config, null, true, true, random).next(OPEN);
            Vec offset = shot.start().subtract(SUBJECT);
            assertEquals(config.distance.blocks, Math.hypot(offset.x(), offset.z()), 1e-9);
            views.add((int)Math.round(Math.toDegrees(Math.atan2(offset.x(), -offset.z()))));
        }
        assertEquals(java.util.Set.of(0, 90, -90, 180), views);
    }

    @Test void aNarrowCorridorRetainsCloseSideViews() {
        Geometry corridor = new Geometry() {
            public boolean clear(Vec from, Vec to, double margin) {
                return Math.max(Math.abs(from.x()), Math.abs(to.x())) + margin < 2.3
                    && Math.max(Math.abs(from.z()), Math.abs(to.z())) + margin < 0.3;
            }
            public boolean visible(Vec from, Vec to, double margin) { return true; }
        };
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.STILL);
        Shot shot = find(new ShotSearch(new Scene(SUBJECT, ENTRY.position(), false, 0), ENTRY, 0,
            config, null, true, false, new Random(4)), corridor);
        assertNotNull(shot, "No close cardinal fallback remained in the corridor");
        assertEquals(2, Math.abs(shot.start().x()), 1e-9);
        assertEquals(0, shot.start().z(), 1e-9);
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
