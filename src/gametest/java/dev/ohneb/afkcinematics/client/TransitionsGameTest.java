package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.Pose;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.world.Difficulty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class TransitionsGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        var limit = new AtomicInteger();
        context.runOnClient(client -> {
            limit.set(client.options.framerateLimit().get());
            client.options.framerateLimit().set(20);
        });
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                floor(server.getPlayerList().getPlayers().getFirst());
            });
            context.waitFor(client -> client.player.onGround());
            context.waitTicks(10);
            start(context);
            context.waitTicks(40);
            var previous = new AtomicReference<Pose>();
            var trace = new ArrayList<String>();
            trace.add("nanoseconds,x,y,z,yaw,pitch,collision_nanoseconds");
            long started = System.nanoTime();
            while (System.nanoTime() - started < 30_000_000_000L) {
                context.runOnClient(client -> {
                    require(AfkCinematics.showing(), "Cinematic stopped during a complete shot transition");
                    var camera = camera(client);
                    var pose = new Pose(WorldGeometry.point(camera.position()), camera.yRot(), camera.xRot());
                    var before = previous.getAndSet(pose);
                    if (before != null) {
                        require(before.position().distance(pose.position()) < 0.3, "Shot transition jumped position");
                        require(Math.abs(Math.IEEEremainder(pose.yaw() - before.yaw(), 360)) < 5, "Shot transition jumped rotation");
                    }
                    long query = System.nanoTime();
                    require(new WorldGeometry(client.level).clear(pose.position(), pose.position()), "Camera entered an obstruction");
                    long duration = System.nanoTime() - query;
                    trace.add((System.nanoTime() - started) + "," + pose.position().x() + "," + pose.position().y()
                        + "," + pose.position().z() + "," + pose.yaw() + "," + pose.pitch() + "," + duration);
                });
                context.waitTicks(1);
            }
            Path artifacts = Path.of(System.getProperty("afkcinematics.test.artifacts"));
            context.takeScreenshot(TestScreenshotOptions.of("camera-after-transition").withDestinationDir(artifacts));
            try { Files.write(artifacts.resolve("camera-trace.csv"), trace); }
            catch (Exception exception) { throw new AssertionError(exception); }
        } finally {
            context.runOnClient(client -> {
                client.options.framerateLimit().set(limit.get());
                focused = null;
            });
        }
    }
}
