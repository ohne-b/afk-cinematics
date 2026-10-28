package dev.ohneb.afkcinematics.platform;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.ohneb.afkcinematics.client.SettingsScreen;

public final class ModMenuIntegration implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() { return SettingsScreen::new; }
}
