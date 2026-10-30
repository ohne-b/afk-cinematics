package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.ConfigStore;
import dev.ohneb.afkcinematics.core.Shot;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.components.Button;
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
                context.runOnClient(client -> go((SettingsScreen)AfkCinematics.screen(), "Camera"));
                context.takeScreenshot(TestScreenshotOptions.of("camera-settings").withDestinationDir(artifacts));
                context.runOnClient(client -> {
                    var settings = (SettingsScreen)AfkCinematics.screen();
                    go(settings, "Activities");
                    press(settings, "Fishing: On cast");
                    require(fixture.config().fishing == dev.ohneb.afkcinematics.core.CinematicConfig.StartMode.OFF, "Fishing control did not change its setting");
                    press(settings, "Fishing: Off");
                    press(settings, "Fishing: Manual");
                    go(settings, "Camera");
                    press(settings, "Shot types: " + Shot.Type.values().length + " enabled");
                    for (Shot.Type type : Shot.Type.values()) {
                        if (type == Shot.Type.PAN) continue;
                        showShotSet(settings, type);
                        String label = Component.translatable("ohneb_afk_cinematics.shot." + type.name().toLowerCase(Locale.ROOT)).getString();
                        press(settings, label + ": ON");
                    }
                    require(fixture.config().shots.equals(EnumSet.of(Shot.Type.PAN)), "Shot controls did not preserve the selected style");
                    showShotSet(settings, Shot.Type.PAN);
                    require(!button(settings, "Panorama: ON").active, "The last enabled shot could be disabled");
                });
                context.takeScreenshot(TestScreenshotOptions.of("shot-options").withDestinationDir(artifacts));
                context.runOnClient(client -> {
                    var settings = (SettingsScreen)AfkCinematics.screen();
                    press(settings, "Moving only");
                    var scenery = EnumSet.allOf(Shot.Type.class);
                    scenery.removeIf(type -> type.travelOnly() || type.fishingOnly());
                    var moving = EnumSet.copyOf(scenery);
                    moving.remove(Shot.Type.PAN);
                    moving.remove(Shot.Type.STILL);
                    require(fixture.config().shots.equals(moving), "Moving-only selection changed another camera set");
                    press(settings, "Enable all");
                    require(fixture.config().shots.equals(scenery), "Enable all changed another camera set");
                    showShotSet(settings, Shot.Type.TRAIL);
                    press(settings, "Enable all");
                    require(fixture.config().shots.contains(Shot.Type.BROADSIDE) && !fixture.config().shots.contains(Shot.Type.ANGLER),
                        "Travel selection changed the fishing set");
                });
                context.takeScreenshot(TestScreenshotOptions.of("travel-shot-set").withDestinationDir(artifacts));
                context.runOnClient(client -> {
                    var settings = (SettingsScreen)AfkCinematics.screen();
                    showShotSet(settings, Shot.Type.ANGLER);
                    press(settings, "Enable all");
                    require(fixture.config().shots.equals(EnumSet.allOf(Shot.Type.class)), "Grouped controls omitted a shot type");
                });
                context.takeScreenshot(TestScreenshotOptions.of("fishing-shot-set").withDestinationDir(artifacts));
                context.runOnClient(client -> {
                    var settings = (SettingsScreen)AfkCinematics.screen();
                    go(settings, "Display");
                    var fps = fpsSlider(settings);
                    client.setLastInputType(InputType.KEYBOARD_TAB);
                    settings.setFocused(fps);
                    fps.setFocused(true);
                    settings.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                    settings.keyPressed(rightArrow());
                    require(fixture.config().cinematicFps == 60, "Navigation changed FPS after leaving slider editing");
                    require(settings.getFocused() == button(settings, "Hide HUD: ON"), "Right-arrow navigation remained trapped on the slider");
                    press(settings, "Hide player names: ON");
                    require(!fixture.config().hidePlayerNames, "Player-name control did not change its setting");
                });
                context.takeScreenshot(TestScreenshotOptions.of("display-settings").withDestinationDir(artifacts));
                context.runOnClient(client -> {
                    var settings = (SettingsScreen)AfkCinematics.screen();
                    go(settings, "Sound");
                    press(settings, "Playlist: Game music");
                    press(settings, "Playlist: Creative music");
                    press(settings, "Playlist: Music discs");
                    var custom = settings.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                    custom.setValue("minecraft:music_disc.cat");
                    require(fixture.config().customMusic.equals("minecraft:music_disc.cat"), "Custom sound edit did not reach settings");
                    go(settings, "Camera");
                    go(settings, "Sound");
                    var restored = settings.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                    require(restored.getValue().equals("minecraft:music_disc.cat"), "Changing pages lost the custom sound");
                });
                context.takeScreenshot(TestScreenshotOptions.of("sound-settings").withDestinationDir(artifacts));
                context.runOnClient(client -> {
                    var settings = (SettingsScreen)AfkCinematics.screen();
                    settings.resize(220, 140);
                    checkBounds(settings);
                    go(settings, "Camera");
                    press(settings, "Shot types: " + Shot.Type.values().length + " enabled");
                    for (Shot.Type type : Shot.Type.values()) {
                        showShotSet(settings, type);
                        String label = Component.translatable("ohneb_afk_cinematics.shot." + type.name().toLowerCase(Locale.ROOT)).getString();
                        button(settings, label + ": ON");
                        checkBounds(settings);
                    }
                    for (String page : new String[]{"Camera", "Display", "AFK", "Activities", "Sound", "Controls"}) {
                        go(settings, page);
                        checkBounds(settings);
                        if (page.equals("Display")) {
                            var fps = fpsSlider(settings);
                            client.setLastInputType(InputType.KEYBOARD_TAB);
                            settings.setFocused(fps);
                            fps.setFocused(true);
                            for (int step = 0; step < 84; step++) settings.keyPressed(rightArrow());
                            require(fixture.config().cinematicFps == 144, "Narrow FPS slider skipped integer values");
                            require(fps.getMessage().getString().equals("AFK FPS: 144"), "FPS slider label was stale");
                        }
                        settings.mouseScrolled(0, 0, 0, -1);
                        checkBounds(settings);
                    }
                    settings.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
                    require(AfkCinematics.screen() == settings, "Back closed settings instead of returning home");
                    button(settings, "Activities");
                    settings.onClose();
                    require(AfkCinematics.screen() == settings, "First save failure hid the error");
                    require(settings.children().stream().anyMatch(child -> child instanceof Button button
                        && button.getMessage().getString().equals("Close without saving")), "Save failure did not offer an exit");
                    settings.onClose();
                    require(AfkCinematics.screen() == parent, "Save failure trapped the settings screen");
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

    private static Button button(SettingsScreen settings, String label) {
        return button(settings, label, label::equals);
    }

    private static Button button(SettingsScreen settings, String label, Predicate<String> match) {
        for (int direction : new int[]{1, -1}) {
            for (int step = 0; step <= Shot.Type.values().length + 2; step++) {
                var found = settings.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> match.test(button.getMessage().getString())).findFirst();
                if (found.isPresent()) return found.get();
                settings.mouseScrolled(0, 0, 0, direction);
            }
        }
        throw new AssertionError("Missing settings control: " + label);
    }

    private static void showShotSet(SettingsScreen settings, Shot.Type type) {
        String target = "For: " + (type.travelOnly() ? "Travel" : type.fishingOnly() ? "Fishing" : "Player & scenery");
        for (int step = 0; step < 3; step++) {
            Button selector = button(settings, "shot-set selector", label -> label.startsWith("For: "));
            if (selector.getMessage().getString().equals(target)) return;
            settings.setFocused(selector);
            selector.setFocused(true);
            selector.onPress(new KeyEvent(InputConstants.KEY_SPACE, 0, 0));
            require(settings.getFocused() instanceof Button focused && focused.getMessage().getString().startsWith("For: "),
                "Switching sets lost keyboard focus");
        }
        throw new AssertionError("Missing shot set: " + target);
    }

    private static AbstractWidget fpsSlider(SettingsScreen settings) {
        return settings.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().startsWith("AFK FPS:")).findFirst().orElseThrow();
    }

    private static KeyEvent rightArrow() {
        //? if >=26.3 {
        /*return new KeyEvent(InputConstants.KEY_RIGHT, 0x40000000 | InputConstants.KEY_RIGHT, 0);
        *///?} else {
        return new KeyEvent(InputConstants.KEY_RIGHT, 0, 0);
        //?}
    }

    private static void go(SettingsScreen settings, String label) {
        for (int step = 0; step < 2; step++) {
            var back = settings.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals("Back")).findFirst();
            if (back.isEmpty()) break;
            back.get().onPress(new KeyEvent(InputConstants.KEY_SPACE, 0, 0));
        }
        press(settings, label);
    }

    private static void press(SettingsScreen settings, String label) {
        Button button = button(settings, label);
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
