package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.ConfigStore;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;

import java.nio.file.Files;
import java.nio.file.Path;

import static dev.ohneb.afkcinematics.client.TestSupport.require;

@SuppressWarnings("UnstableApiUsage")
public final class SettingsGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try {
            var directory = Files.createTempDirectory(Path.of(System.getProperty("afkcinematics.test.artifacts")), "settings-failure-");
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
