package dev.ohneb.afkcinematics.smoke;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "ohneb_afk_cinematics_smoke", dist = Dist.CLIENT)
public final class NeoForgeSmoke {
    public NeoForgeSmoke() { NeoForge.EVENT_BUS.addListener(NeoForgeSmoke::tick); }
    private static void tick(ClientTickEvent.Post event) { SmokeProbe.tick("neoforge"); }
}
