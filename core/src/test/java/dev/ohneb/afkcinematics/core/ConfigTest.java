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
        Files.writeString(path, "{\"future\":{\"value\":1},\"idleSeconds\":9999,\"motion\":null,\"hideHud\":\"yes\"}");
        var store = new ConfigStore(path);
        assertTrue(store.readable());
        assertEquals(600, store.config().idleSeconds);
        assertEquals(CinematicConfig.Motion.GENTLE, store.config().motion);
        assertTrue(store.config().hideHud);
        store.config().distance = CinematicConfig.Distance.CLOSE;
        assertTrue(store.save());
        assertTrue(Files.readString(path).contains("\"future\""));
        assertEquals(CinematicConfig.Distance.CLOSE, new ConfigStore(path).config().distance);
    }

    @Test void reportsAnUnwritableDestinationWithoutRemovingIt() throws Exception {
        Path path = directory.resolve("directory");
        var store = new ConfigStore(path);
        Files.createDirectory(path);
        assertTrue(store.readable());
        assertFalse(store.save());
        assertTrue(Files.isDirectory(path));
    }

    @Test void shotOptionsRemainCompatibleWithExistingSettings() throws Exception {
        Path path = directory.resolve("settings.json");
        Files.writeString(path, "{\"music\":true,\"shots\":[\"pan\",null,\"future_shot\",\"crane\"],\"pace\":\"long\"}");
        var store = new ConfigStore(path);
        assertTrue(store.readable());
        assertTrue(store.config().music);
        assertEquals(EnumSet.of(Shot.Type.PAN, Shot.Type.CRANE), store.config().shots);
        assertEquals(CinematicConfig.Pace.LONG, store.config().pace);
        assertTrue(store.save());
        assertEquals(store.config().shots, new ConfigStore(path).config().shots);
        Files.writeString(path, "{\"shots\":[],\"pace\":\"invalid\"}");
        var defaults = new ConfigStore(path).config();
        assertEquals(EnumSet.allOf(Shot.Type.class), defaults.shots);
        assertEquals(CinematicConfig.Pace.RELAXED, defaults.pace);
    }
}
