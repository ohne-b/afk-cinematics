package dev.ohneb.afkcinematics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/** Makes direct F1 reads agree with cinematic visibility without retaining an option change. */
public final class HudVisibility {
    private HudVisibility() { }

    //? if <26.2 {
    private static Options owner;
    private static boolean hidden, rendering;
    private static int depth;

    public static void render(Runnable action) {
        if (owner == null && !AfkCinematics.hideHud()) { action.run(); return; }
        scope(true, action);
    }

    public static void input(Runnable action) {
        if (owner == null) { action.run(); return; }
        scope(false, action);
    }

    private static void scope(boolean render, Runnable action) {
        if (owner == null) {
            owner = Minecraft.getInstance().options;
            hidden = owner.hideGui;
        } else if (!rendering) hidden = owner.hideGui;
        boolean previous = rendering;
        rendering = render;
        depth++;
        owner.hideGui = hidden || rendering && AfkCinematics.hideHud();
        try { action.run(); }
        finally {
            // Reentrant F1 input updates the real value, including across nested renders.
            if (!rendering) hidden = owner.hideGui;
            rendering = previous;
            depth--;
            owner.hideGui = hidden || depth > 0 && rendering && AfkCinematics.hideHud();
            if (depth == 0) owner = null;
        }
    }
    //?}
}
