package dev.ohneb.afkcinematics.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.EnumSet;

public final class ConfigStore {
    private final Path path;
    private JsonObject document = new JsonObject();
    private boolean readable = true;
    private String error = "";
    private final CinematicConfig config = new CinematicConfig();

    public ConfigStore(Path path) {
        this.path = path;
        if (!Files.exists(path)) return;
        try {
            document = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            config.enabled = bool("enabled", true);
            config.automatic = bool("automatic", true);
            config.idleSeconds = Math.clamp(integer("idleSeconds", 30), 10, 600);
            config.motion = enumeration("motion", CinematicConfig.Motion.GENTLE);
            config.subject = enumeration("subject", CinematicConfig.Subject.MIXED);
            config.pace = enumeration("pace", CinematicConfig.Pace.RELAXED);
            config.shots = shots();
            config.distance = enumeration("distance", CinematicConfig.Distance.NORMAL);
            config.lens = enumeration("lens", CinematicConfig.Lens.CINEMATIC);
            config.hideHud = bool("hideHud", true);
            config.letterbox = bool("letterbox", false);
            config.music = bool("music", false);
            config.unfocused = bool("unfocused", false);
        } catch (IOException | RuntimeException exception) {
            readable = false;
            config.enabled = false;
            error = "Could not read settings; the existing file was preserved.";
        }
    }

    public CinematicConfig config() { return config; }
    public boolean readable() { return readable; }
    public String error() { return error; }

    private boolean bool(String key, boolean fallback) {
        var value = document.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : fallback;
    }

    private int integer(String key, int fallback) {
        try {
            var value = document.get(key);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsBigDecimal().intValueExact() : fallback;
        } catch (ArithmeticException | NumberFormatException exception) { return fallback; }
    }

    private <T extends Enum<T>> T enumeration(String key, T fallback) {
        try {
            return document.has(key) ? Enum.valueOf(fallback.getDeclaringClass(), document.get(key).getAsString().toUpperCase(Locale.ROOT)) : fallback;
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException exception) { return fallback; }
    }

    private EnumSet<Shot.Type> shots() {
        var selected = EnumSet.noneOf(Shot.Type.class);
        if (document.get("shots") instanceof JsonArray array) {
            for (var value : array) {
                try { selected.add(Shot.Type.valueOf(value.getAsString().toUpperCase(Locale.ROOT))); }
                catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException ignored) { }
            }
        }
        return selected.isEmpty() ? EnumSet.allOf(Shot.Type.class) : selected;
    }

    public boolean save() {
        if (!readable) return false;
        JsonObject updated = document.deepCopy();
        updated.addProperty("enabled", config.enabled);
        updated.addProperty("automatic", config.automatic);
        updated.addProperty("idleSeconds", Math.clamp(config.idleSeconds, 10, 600));
        updated.addProperty("motion", config.motion.name().toLowerCase(Locale.ROOT));
        updated.addProperty("subject", config.subject.name().toLowerCase(Locale.ROOT));
        updated.addProperty("pace", config.pace.name().toLowerCase(Locale.ROOT));
        var shots = new JsonArray();
        config.shots.forEach(type -> shots.add(type.name().toLowerCase(Locale.ROOT)));
        updated.add("shots", shots);
        updated.addProperty("distance", config.distance.name().toLowerCase(Locale.ROOT));
        updated.addProperty("lens", config.lens.name().toLowerCase(Locale.ROOT));
        updated.addProperty("hideHud", config.hideHud);
        updated.addProperty("letterbox", config.letterbox);
        updated.addProperty("music", config.music);
        updated.addProperty("unfocused", config.unfocused);
        Path temporary = null;
        try {
            Path directory = path.toAbsolutePath().getParent();
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, "afk-cinematics-", ".tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(updated) + "\n", StandardCharsets.UTF_8);
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            document = updated;
            error = "";
            return true;
        } catch (IOException exception) {
            error = "Could not save settings.";
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ignored) { }
            }
        }
    }
}
