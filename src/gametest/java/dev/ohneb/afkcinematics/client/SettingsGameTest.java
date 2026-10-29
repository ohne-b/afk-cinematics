package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.ConfigStore;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;

import java.nio.file.Files;
import java.nio.file.Path;

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
            try {
                context.runOnClient(client -> {
                    try { field.set(null, fixture); }
                    catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
                    var parent = AfkCinematics.screen();
                    var settings = new SettingsScreen(parent);
                    AfkCinematics.showScreen(settings);
                    settings.resize(220, 140);
                    var widgets = settings.children().stream().filter(AbstractWidget.class::isInstance)
                        .map(AbstractWidget.class::cast).toList();
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
}
