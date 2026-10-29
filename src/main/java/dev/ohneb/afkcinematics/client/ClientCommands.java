package dev.ohneb.afkcinematics.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public final class ClientCommands {
    private ClientCommands() { }

    public static <S> LiteralArgumentBuilder<S> root() {
        return LiteralArgumentBuilder.<S>literal("afkcinematics")
            .executes(context -> { AfkCinematics.scheduleSettings(); return 1; })
            .then(LiteralArgumentBuilder.<S>literal("settings").executes(context -> { AfkCinematics.scheduleSettings(); return 1; }))
            .then(LiteralArgumentBuilder.<S>literal("start").executes(context -> { AfkCinematics.requestStart(); return 1; }))
            .then(LiteralArgumentBuilder.<S>literal("stop").executes(context -> { AfkCinematics.stop(); return 1; }));
    }
}
