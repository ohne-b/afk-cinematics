package dev.ohneb.afkcinematics.compat;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/** Only the inspected AmbientSounds water-analysis contracts are adapted. */
public final class AmbientSoundsPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!mixin.endsWith(".AmbientSoundsMixin")) return true;
        //? if fabric && (=1.21.11 || =26.3) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("ambientsounds")
            .map(mod -> supportedVersion(mod.getMetadata().getVersion().getFriendlyString())).orElse(false);
        //?} else if neoforge && =26.3 {
        /*return net.neoforged.fml.loading.FMLLoader.getCurrent().getLoadingModList().getMods().stream()
            .anyMatch(mod -> mod.getModId().equals("ambientsounds") && supportedVersion(mod.getVersion().toString()));
        *///?} else {
        /*return false;
        *///?}
    }

    public static boolean supportedVersion(String version) {
        //? if fabric && =1.21.11 {
        return version.equals("6.3.5");
        //?} else if =26.3 {
        /*return version.equals("6.3.11");
        *///?} else {
        /*return false;
        *///?}
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) { }
}
