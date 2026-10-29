package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.compat.AmbientSoundsPlugin;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import static dev.ohneb.afkcinematics.client.TestSupport.require;

/** Exercises the actual optional mod without a production dependency on it. */
final class AmbientWaterChecks {
    private AmbientWaterChecks() { }

    static void drySnapshot(Minecraft client) {
        if (!FabricLoader.getInstance().isModLoaded("ambientsounds")) return;
        try {
            var position = AfkCinematics.class.getDeclaredField("audioPosition");
            var submerged = AfkCinematics.class.getDeclaredField("audioSubmerged");
            position.setAccessible(true);
            submerged.setAccessible(true);
            Object saved = position.get(null);
            boolean wasSubmerged = submerged.getBoolean(null);
            try {
                var dry = client.player.getEyePosition().add(0, 12, 0);
                position.set(null, dry);
                submerged.setBoolean(null, false);
                require(client.player.isUnderWater(), "Dry snapshot fixture needs a wet player");
                verify(client, dry);
            } finally {
                position.set(null, saved);
                submerged.setBoolean(null, wasSubmerged);
            }
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    static void verify(Minecraft client) {
        verify(client, AfkCinematics.showing() ? TestSupport.camera(client).position() : client.player.getEyePosition());
    }

    private static void verify(Minecraft client, net.minecraft.world.phys.Vec3 expectedPosition) {
        if (!FabricLoader.getInstance().isModLoaded("ambientsounds")) return;
        try {
            require(!AmbientSoundsPlugin.supportedVersion("unknown"), "Unknown AmbientSounds version is enabled");
            Object handler = Class.forName("team.creative.ambientsounds.AmbientSounds").getField("TICK_HANDLER").get(null);
            Object engine = handler.getClass().getField("engine").get(handler);
            Object environment = handler.getClass().getField("environment").get(handler);
            Object sounds = handler.getClass().getField("soundEngine").get(handler);
            require(environment != null && engine != null, "AmbientSounds did not initialize");
            BlockPos origin = BlockPos.containing(expectedPosition);
            var fluid = client.level.getFluidState(origin);
            int expected = 0;
            if (fluid.is(FluidTags.WATER) && expectedPosition.y < origin.getY() + fluid.getHeight(client.level, origin)) {
                for (BlockPos block = origin; client.level.getFluidState(block).is(FluidTags.WATER); block = block.above())
                    expected++;
            }
            //? if >=26.3 {
            /*var analyze = environment.getClass().getMethod("analyzeUnderwater", engine.getClass(), Player.class, Level.class);
            analyze.invoke(environment, engine, client.player, client.level);
            *///?} else {
            var analyze = environment.getClass().getMethod("analyzeUnderwater", Player.class, Level.class);
            analyze.invoke(environment, client.player, client.level);
            //?}
            require(environment.getClass().getField("underwater").getDouble(environment) == expected,
                "AmbientSounds used the player's water depth instead of the applied view: expected " + expected);
            //? if >=26.3 {
            /*var tags = engine.getClass().getField("considerWater");
            Object saved = tags.get(engine);
            try {
                tags.set(engine, java.util.List.of(FluidTags.LAVA));
                analyze.invoke(environment, engine, client.player, client.level);
                require(environment.getClass().getField("underwater").getDouble(environment) == 0,
                    "Water snapshot incorrectly matched AmbientSounds' configured lava tag");
            } finally {
                tags.set(engine, saved);
                analyze.invoke(environment, engine, client.player, client.level);
            }
            *///?}
            require(engine == handler.getClass().getField("engine").get(handler)
                && sounds == handler.getClass().getField("soundEngine").get(handler), "Water sampling replaced AmbientSounds' engine");
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
}
