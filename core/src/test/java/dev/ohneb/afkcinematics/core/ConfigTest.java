package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Files.createDirectory(path);
        var store = new ConfigStore(path);
        assertFalse(store.save());
        assertTrue(Files.isDirectory(path));
    }
}
