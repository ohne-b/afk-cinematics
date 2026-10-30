package dev.ohneb.afkcinematics.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public final class ClientCommands {
    private ClientCommands() { }

    public static <S> LiteralArgumentBuilder<S> root() {
        return LiteralArgumentBuilder.<S>literal("afkcinematics")
            .executes(context -> { AfkCinematics.scheduleSettings(); return 1; })
            .then(LiteralArgumentBuilder.<S>literal("settings").executes(context -> { AfkCinematics.scheduleSettings(); return 1; }))
            .then(LiteralArgumentBuilder.<S>literal("start").executes(context -> { AfkCinematics.requestStart(); return 1; }))
            .then(LiteralArgumentBuilder.<S>literal("next").executes(context -> { AfkCinematics.nextShot(); return 1; }))
            .then(LiteralArgumentBuilder.<S>literal("music")
                .executes(context -> CinematicMusic.enabled(!AfkCinematics.config().music))
                .then(LiteralArgumentBuilder.<S>literal("on").executes(context -> CinematicMusic.enabled(true)))
                .then(LiteralArgumentBuilder.<S>literal("off").executes(context -> CinematicMusic.enabled(false)))
                .then(LiteralArgumentBuilder.<S>literal("next").executes(context -> { CinematicMusic.next(); return 1; })))
            .then(LiteralArgumentBuilder.<S>literal("stop").executes(context -> { AfkCinematics.stop(); return 1; }));
    }
}
