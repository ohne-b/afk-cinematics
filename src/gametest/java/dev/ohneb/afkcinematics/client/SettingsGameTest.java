package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.ConfigStore;
import dev.ohneb.afkcinematics.core.Shot;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.InputType;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.EnumSet;
import java.util.function.Predicate;

import static dev.ohneb.afkcinematics.client.TestSupport.require;

@SuppressWarnings("UnstableApiUsage")
public final class SettingsGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try {
            var artifacts = Files.createDirectories(Path.of(System.getProperty("afkcinematics.test.artifacts")));
            var directory = Files.createTempDirectory(artifacts, "settings-failure-");
            var path = directory.resolve("settings.json");
            var fixture = new ConfigStore(path);
            Files.createDirectory(path);
            var marker = path.resolve("preserved.txt");
            Files.writeString(marker, "Keep this file.");
            var field = AfkCinematics.class.getDeclaredField("store");
            field.setAccessible(true);
            var previous = AfkCinematics.store();
            var parent = context.computeOnClient(client -> AfkCinematics.screen());
            try {
                context.runOnClient(client -> {
                    try { field.set(null, fixture); }
                    catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
                    var settings = new SettingsScreen(parent);
                    AfkCinematics.showScreen(settings);
                });
                context.takeScreenshot(TestScreenshotOptions.of("settings-home").withDestinationDir(artifacts));
                context.runOnClient(client -> {
                    var settings = (SettingsScreen)AfkCinematics.screen();
                    press(settings, "Enabled: ON");
                    require(!fixture.config().enabled, "Enabled toggle did not change");
                    press(settings, "Enabled: OFF");
                    press(settings, "Front travel shots: ON");
                    require(!fixture.config().frontTravel, "Front-view preference did not change");
                    go(settings, "Shots");
                    showShotSet(settings, Shot.Type.LEAD);
                    require(!button(settings, Component.translatable("ohneb_afk_cinematics.shot.lead").getString() + ": ON").active,
                        "The front-only shot remained available after turning off front views");
                    go(settings, "General");
                    press(settings, "Front travel shots: OFF");
                    press(settings, "Start when fishing: ON");
                    require(!fixture.config().autoFishing, "Fishing start setting did not change");
                    go(settings, "AFK");
                    field(settings, "seconds idle").setValue("45");
                    require(fixture.config().idleSeconds == 45, "Typed idle delay was lost");
                    field(settings, "FPS while AFK").setValue("144");
                    require(fixture.config().cinematicFps == 144, "Exact FPS entry was lost");
                    int originalWidth = settings.width, originalHeight = settings.height;
                    settings.resize(220, 140);
                    field(settings, "FPS while AFK").setValue("30");
                    field(settings, "FPS while AFK").setValue("300");
                    go(settings, "General");
                    for (int i = 0; i < 30; i++) settings.mouseScrolled(0, 0, 0, 1);
                    var done = button(settings, "Done");
                    settings.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(done.getX() + 5, done.getY() + 5,
                        new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                    settings.tick();
                    var invalidFps = settings.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                        .filter(input -> input.getMessage().getString().equals("FPS while AFK")).findFirst().orElse(null);
                    require(AfkCinematics.screen() == settings && invalidFps != null && invalidFps.getValue().equals("300"),
                        "Rebuilding settings silently saved a numeric prefix");
                    require(settings.getFocused() == invalidFps, "Invalid hidden number did not regain focus");
                    invalidFps.setValue("144");
                    settings.resize(originalWidth, originalHeight);
                    var subject = button(settings, "Subject: Mixed");
                    settings.setFocused(subject);
                    subject.setFocused(true);
                    subject.onPress(new KeyEvent(InputConstants.KEY_SPACE, 0, 0));
                    require(settings.getFocused() == button(settings, "Subject: Player"), "Changing subject lost focus");
                    require(!button(settings, "Occasional wildlife: ON").active, "Player-only AFK retained wildlife");
                    button(settings, "Subject: Player").onPress(new KeyEvent(InputConstants.KEY_SPACE, 0, 1));
                    var mixed = button(settings, "Subject: Mixed");
                    settings.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mixed.getX() + 5, mixed.getY() + 5,
                        new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                    require(settings.getFocused() == button(settings, "Subject: Player"), "Mouse cycling left focus on a replaced control");
                    button(settings, "Subject: Player").onPress(new KeyEvent(InputConstants.KEY_SPACE, 0, 1));
                    go(settings, "Shots");
                    for (Shot.Type type : Shot.Type.values()) {
                        if (type == Shot.Type.PAN) continue;
                        showShotSet(settings, type);
                        press(settings, Component.translatable("ohneb_afk_cinematics.shot." + type.name().toLowerCase(Locale.ROOT)).getString() + ": ON");
                    }
                    require(fixture.config().shots.equals(EnumSet.of(Shot.Type.PAN)), "Shot selection lost a choice");
                    showShotSet(settings, Shot.Type.PAN);
                    require(!button(settings, "Panorama: ON").active, "Last shot could be disabled");
                    press(settings, "Moving only");
                    require(!fixture.config().shots.contains(Shot.Type.PAN) && !fixture.config().shots.contains(Shot.Type.TRAIL), "Preset changed another group");
                    press(settings, "Enable all");
                    showShotSet(settings, Shot.Type.TRAIL);
                    press(settings, "Enable all");
                    showShotSet(settings, Shot.Type.ANGLER);
                    press(settings, "Enable all");
                    go(settings, "Camera");
                    require(!button(settings, "Camera").active, "Sidebar did not mark the current category");
                });
                context.takeScreenshot(TestScreenshotOptions.of("camera-settings").withDestinationDir(artifacts));
                context.runOnClient(client -> {
                    var settings = (SettingsScreen)AfkCinematics.screen();
                    go(settings, "Music");
                    press(settings, "Cinematic music: OFF");
                    press(settings, "Playlist: Game music");
                    press(settings, "Playlist: Creative music");
                    press(settings, "Playlist: Music discs");
                    field(settings, Component.translatable("ohneb_afk_cinematics.customMusic").getString()).setValue("minecraft:music_disc.cat");
                    go(settings, "Camera");
                    go(settings, "Music");
                    require(field(settings, Component.translatable("ohneb_afk_cinematics.customMusic").getString()).getValue().equals("minecraft:music_disc.cat"), "Collapsing music lost the sound event");
                });
                context.takeScreenshot(TestScreenshotOptions.of("sound-settings").withDestinationDir(artifacts));
                context.runOnClient(client -> {
                    var settings = (SettingsScreen)AfkCinematics.screen();
                    settings.resize(220, 140);
                    for (String section : new String[]{"General", "Camera", "AFK", "Music", "Shots"}) {
                        go(settings, section);
                        for (int i = 0; i < 20; i++) { checkBounds(settings); settings.mouseScrolled(0, 0, 0, -1); }
                    }
                    go(settings, "Shots");
                    for (Shot.Type type : Shot.Type.values()) {
                        showShotSet(settings, type);
                        button(settings, Component.translatable("ohneb_afk_cinematics.shot." + type.name().toLowerCase(Locale.ROOT)).getString() + ": ON");
                        checkBounds(settings);
                    }
                    go(settings, "General");
                    var bindings = button(settings, "toggle binding", label -> label.startsWith("Start / stop:"));
                    settings.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(bindings.getX() + 5, bindings.getY() + 5,
                        new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                    settings.tick();
                    require(AfkCinematics.screen() == settings, "Failed save opened key bindings");
                    require(settings.getFocused() == button(settings, "Close without saving"), "Save failure lost focus");
                    settings.onClose();
                    require(AfkCinematics.screen() == parent, "Save error trapped settings");
                });
                require(Files.readString(marker).equals("Keep this file."), "Failed save damaged existing data");
            } finally {
                context.runOnClient(client -> {
                    try { field.set(null, previous); }
                    catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
                });
            }
        } catch (Exception exception) { throw new AssertionError(exception); }
    }

    private static AbstractButton button(SettingsScreen settings, String label) {
        return button(settings, label, label::equals);
    }

    private static AbstractButton button(SettingsScreen settings, String label, Predicate<String> match) {
        for (int direction : new int[]{1, -1}) {
            for (int step = 0; step <= Shot.Type.values().length + 2; step++) {
                var found = settings.children().stream().filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                    .filter(button -> match.test(button.getMessage().getString())).findFirst();
                if (found.isPresent()) return found.get();
                settings.mouseScrolled(0, 0, 0, direction);
            }
        }
        throw new AssertionError("Missing settings control: " + label);
    }

    private static void showShotSet(SettingsScreen settings, Shot.Type type) {
        String target = "For: " + (type.travelOnly() ? "Following" : type.fishingOnly() ? "Fishing" : "Player & scenery");
        Predicate<String> group = label -> label.startsWith("For: ");
        for (int step = 0; step < 3; step++) {
            AbstractButton selector = button(settings, "shot-set selector", group);
            if (selector.getMessage().getString().equals(target)) return;
            settings.setFocused(selector);
            selector.setFocused(true);
            selector.onPress(new KeyEvent(InputConstants.KEY_SPACE, 0, 0));
            require(settings.getFocused() instanceof AbstractButton focused && group.test(focused.getMessage().getString()),
                "Switching sets lost keyboard focus");
        }
        throw new AssertionError("Missing shot set: " + target);
    }

    private static EditBox field(SettingsScreen settings, String label) {
        for (int direction : new int[]{1, -1}) for (int step = 0; step < 30; step++) {
            var found = settings.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(input -> input.getMessage().getString().equals(label)).findFirst();
            if (found.isPresent()) return found.get();
            settings.mouseScrolled(0, 0, 0, direction);
        }
        throw new AssertionError("Missing field: " + label);
    }

    private static void go(SettingsScreen settings, String label) {
        var tab = button(settings, label);
        if (tab.active) tab.onPress(new KeyEvent(InputConstants.KEY_SPACE, 0, 0));
        settings.tick();
    }

    private static void press(SettingsScreen settings, String label) {
        AbstractButton button = button(settings, label);
        require(button.active, "Settings control was unexpectedly disabled: " + label);
        button.onPress(new KeyEvent(InputConstants.KEY_SPACE, 0, 0));
    }

    private static void checkBounds(SettingsScreen settings) {
        var widgets = settings.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
        for (var widget : widgets) {
            require(widget.getX() >= 0 && widget.getX() + widget.getWidth() <= 220
                && widget.getY() >= 0 && widget.getY() + widget.getHeight() <= 140, "Narrow settings clipped a control");
            for (var other : widgets) {
                if (other == widget) continue;
                require(widget.getX() >= other.getX() + other.getWidth() || other.getX() >= widget.getX() + widget.getWidth()
                    || widget.getY() >= other.getY() + other.getHeight() || other.getY() >= widget.getY() + widget.getHeight(),
                    "Narrow settings overlapped controls");
            }
        }
    }
}
