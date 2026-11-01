package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    @TempDir Path directory;

    @Test void preservesUnreadableFiles() throws Exception {
        Path path = directory.resolve("settings.json");
        Files.writeString(path, "{broken");
        var store = new ConfigStore(path);
        assertFalse(store.readable());
        assertFalse(store.config().enabled);
        assertFalse(store.save());
        assertEquals("{broken", Files.readString(path));
    }

    @Test void keepsUnknownFieldsAndValidatesIndividualValues() throws Exception {
        Path path = directory.resolve("settings.json");
        Files.writeString(path, "{\"future\":{\"value\":1},\"distance\":\"close\",\"idleSeconds\":9999,\"motion\":null,\"hideHud\":\"yes\"}");
        var store = new ConfigStore(path);
        assertTrue(store.readable());
        assertEquals(600, store.config().idleSeconds);
        assertEquals(CinematicConfig.Motion.GENTLE, store.config().motion);
        assertTrue(store.config().hideHud);
        assertTrue(store.config().hidePlayerNames);
        store.config().hidePlayerNames = false;
        assertTrue(store.config().keepChat);
        store.config().keepChat = false;
        assertTrue(store.save());
        assertFalse(new ConfigStore(path).config().hidePlayerNames);
        assertFalse(new ConfigStore(path).config().keepChat);
        assertTrue(Files.readString(path).contains("\"future\""));
        assertEquals(600, new ConfigStore(path).config().idleSeconds);
    }

    @Test void wildlifeAndAfkChoicesPersist() throws Exception {
        Path path = directory.resolve("afk.json");
        var store = new ConfigStore(path);
        assertTrue(store.config().wildlife);
        assertTrue(store.config().autoFishing);
        assertTrue(store.config().frontTravel);
        store.config().wildlife = false;
        store.config().automatic = false;
        store.config().autoFishing = false;
        store.config().frontTravel = false;
        assertTrue(store.save());
        var restored = new ConfigStore(path).config();
        assertFalse(restored.wildlife);
        assertFalse(restored.automatic);
        assertFalse(restored.autoFishing);
        assertFalse(restored.frontTravel);
    }

    @Test void reportsAnUnwritableDestinationWithoutRemovingIt() throws Exception {
        Path path = directory.resolve("directory");
        var store = new ConfigStore(path);
        Files.createDirectory(path);
        assertTrue(store.readable());
        assertFalse(store.save());
        assertTrue(Files.isDirectory(path));
    }

    @Test void shotOptionsPersistAndNewShotsAreEnabled() throws Exception {
        Path path = directory.resolve("settings.json");
        Files.writeString(path, "{\"music\":true,\"shots\":[\"pan\",null,\"future_shot\",\"crane\"],\"pace\":\"long\"}");
        var store = new ConfigStore(path);
        assertTrue(store.readable());
        assertTrue(store.config().music);
        assertEquals(EnumSet.of(Shot.Type.PAN, Shot.Type.CRANE), store.config().shots);
        assertEquals(CinematicConfig.Pace.LONG, store.config().pace);
        assertEquals(CinematicConfig.Transition.CUT, store.config().transition);
        store.config().transition = CinematicConfig.Transition.FADE;
        assertEquals(CinematicConfig.Subject.MIXED, store.config().subject);
        assertTrue(store.config().useGameFov);
        assertEquals(90, store.config().customFov);
        store.config().subject = CinematicConfig.Subject.SCENERY;
        store.config().playerShare = 75;
        store.config().useGameFov = false;
        store.config().customFov = 140;
        store.config().soundtrack = CinematicConfig.Soundtrack.CUSTOM;
        store.config().customMusic = "example:quiet_music";
        store.config().musicVolume = 35;
        store.config().shots = EnumSet.of(Shot.Type.FLYOVER, Shot.Type.LOW_PASS, Shot.Type.REVEAL);
        assertTrue(store.save());
        assertEquals(store.config().shots, new ConfigStore(path).config().shots);
        assertEquals(75, new ConfigStore(path).config().playerShare);
        var saved = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        var catalog = saved.getAsJsonArray("knownShots");
        catalog.remove(catalog.size() - 1);
        Files.writeString(path, saved.toString());
        var upgraded = EnumSet.copyOf(store.config().shots);
        upgraded.add(Shot.Type.values()[Shot.Type.values().length - 1]);
        assertEquals(upgraded, new ConfigStore(path).config().shots);
        assertEquals(CinematicConfig.Subject.SCENERY, new ConfigStore(path).config().subject);
        assertEquals(CinematicConfig.Transition.FADE, new ConfigStore(path).config().transition);
        assertFalse(new ConfigStore(path).config().useGameFov);
        assertEquals(140, new ConfigStore(path).config().customFov);
        assertEquals(CinematicConfig.Soundtrack.CUSTOM, new ConfigStore(path).config().soundtrack);
        assertEquals("example:quiet_music", new ConfigStore(path).config().customMusic);
        assertEquals(35, new ConfigStore(path).config().musicVolume);
        Files.writeString(path, "{\"shots\":[],\"pace\":\"invalid\",\"transition\":\"invalid\"}");
        var defaults = new ConfigStore(path).config();
        assertEquals(EnumSet.allOf(Shot.Type.class), defaults.shots);
        assertEquals(CinematicConfig.Pace.RELAXED, defaults.pace);
        assertEquals(CinematicConfig.Transition.CUT, defaults.transition);
        assertTrue(defaults.useGameFov);
        for (String invalid : new String[]{"null", "\"140\"", "140.5", "1e30"}) {
            Files.writeString(path, "{\"customFov\":" + invalid + "}");
            assertEquals(90, new ConfigStore(path).config().customFov);
        }
        for (int fov : new int[]{0, 30, 90, 140, 999}) {
            Files.writeString(path, "{\"customFov\":" + fov + "}");
            assertEquals(Math.clamp(fov, 30, 140), new ConfigStore(path).config().customFov);
        }
    }

    @Test void cinematicFrameCapPersistsAndRejectsInvalidValues() throws Exception {
        Path path = directory.resolve("fps.json");
        assertEquals(60, new ConfigStore(path).config().cinematicFps);
        for (String invalid : new String[]{"null", "\"144\"", "144.5", "1e30"}) {
            Files.writeString(path, "{\"cinematicFps\":" + invalid + "}");
            assertEquals(60, new ConfigStore(path).config().cinematicFps);
        }
        for (int cap : new int[]{0, 20, 60, 144, 240, 999}) {
            Files.writeString(path, "{\"cinematicFps\":" + cap + "}");
            var store = new ConfigStore(path);
            assertEquals(Math.clamp(cap, 20, 240), store.config().cinematicFps);
            store.config().cinematicFps = cap;
            assertTrue(store.save());
            assertEquals(Math.clamp(cap, 20, 240), new ConfigStore(path).config().cinematicFps);
        }
    }
}
