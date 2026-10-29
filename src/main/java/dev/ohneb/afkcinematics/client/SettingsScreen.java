package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.core.Shot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
//?}
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class SettingsScreen extends Screen {
    private enum Page { GENERAL, CAMERA, DISPLAY, SHOTS }
    private final Screen parent;
    private final EnumMap<Shot.Type, Button> shotButtons = new EnumMap<>(Shot.Type.class);
    private Page page = Page.GENERAL;
    private int offset;
    private int maxOffset;
    private Component error = Component.empty();
    private boolean saveFailed;
    private Button close;

    public SettingsScreen(Screen parent) {
        super(text("settings"));
        this.parent = parent;
        AfkCinematics.stop();
    }

    @Override protected void init() {
        CinematicConfig config = AfkCinematics.config();
        int columns = width >= 320 ? 2 : 1;
        int contentWidth = Math.min(width - 24, 320);
        int buttonWidth = (contentWidth - (columns - 1) * 6) / columns;
        int x = (width - contentWidth) / 2;
        int tabWidth = (contentWidth - 8) / 3;
        for (int index = 0; index < 3; index++) {
            Page tab = Page.values()[index];
            var button = Button.builder(text("page." + tab.name().toLowerCase(Locale.ROOT)), ignored -> {
                page = tab;
                offset = 0;
                rebuildWidgets();
            }).bounds(x + index * (tabWidth + 4), 28, index == 2 ? contentWidth - 2 * (tabWidth + 4) : tabWidth, 20).build();
            button.active = page != tab;
            addRenderableWidget(button);
        }
        shotButtons.clear();
        List<AbstractWidget> widgets = controls(config, buttonWidth);
        int visibleRows = Math.max(1, (height - 108) / 22);
        maxOffset = Math.max(0, (widgets.size() + columns - 1) / columns - visibleRows);
        offset = Math.clamp(offset, 0, maxOffset);
        for (int index = 0; index < widgets.size(); index++) {
            int row = index / columns - offset;
            if (row < 0 || row >= visibleRows) continue;
            AbstractWidget widget = widgets.get(index);
            widget.setX(x + (index % columns) * (buttonWidth + 6));
            widget.setY(54 + row * 22);
            widget.setWidth(buttonWidth);
            widget.active &= AfkCinematics.store().readable();
            addRenderableWidget(widget);
        }
        int half = (contentWidth - 6) / 2;
        var preview = Button.builder(text("preview"), button -> {
            if (save()) { AfkCinematics.showScreen(null); AfkCinematics.requestStart(); }
        }).bounds(width / 2 - half - 3, height - 26, half, 20).build();
        preview.active = minecraft.level != null && config.enabled && AfkCinematics.conflict().isEmpty() && AfkCinematics.store().readable();
        preview.setTooltip(Tooltip.create(text("preview.help")));
        addRenderableWidget(preview);
        close = addRenderableWidget(Button.builder(saveFailed ? text("close_unsaved") : CommonComponents.GUI_DONE, button -> onClose())
            .bounds(width / 2 + 3, height - 26, half, 20).build());
    }

    private List<AbstractWidget> controls(CinematicConfig config, int buttonWidth) {
        List<AbstractWidget> widgets = new ArrayList<>();
        switch (page) {
            case GENERAL -> {
                widgets.add(toggle("enabled", () -> config.enabled, value -> config.enabled = value));
                widgets.add(toggle("automatic", () -> config.automatic, value -> config.automatic = value));
                widgets.add(new AbstractSliderButton(0, 0, buttonWidth, 20, Component.empty(), (config.idleSeconds - 10) / 590.0) {
                    { updateMessage(); }
                    @Override protected void updateMessage() { setMessage(text("delay", (int)Math.round(value * 590) + 10)); }
                    @Override protected void applyValue() { config.idleSeconds = (int)Math.round(value * 590) + 10; }
                });
                widgets.getLast().setTooltip(Tooltip.create(text("delay.help")));
                widgets.add(toggle("unfocused", () -> config.unfocused, value -> config.unfocused = value));
            }
            case CAMERA -> {
                widgets.add(Button.builder(value("subject", config.subject), button -> {
                    config.subject = CinematicConfig.Subject.values()[(config.subject.ordinal() + 1) % CinematicConfig.Subject.values().length];
                    button.setMessage(value("subject", config.subject));
                }).tooltip(Tooltip.create(text("subject.help"))).build());
                widgets.add(Button.builder(value("motion", config.motion), button -> {
                    config.motion = CinematicConfig.Motion.values()[(config.motion.ordinal() + 1) % CinematicConfig.Motion.values().length];
                    button.setMessage(value("motion", config.motion));
                }).tooltip(Tooltip.create(text("motion.help"))).build());
                widgets.add(Button.builder(value("pace", config.pace), button -> {
                    config.pace = CinematicConfig.Pace.values()[(config.pace.ordinal() + 1) % CinematicConfig.Pace.values().length];
                    button.setMessage(value("pace", config.pace));
                    button.setTooltip(paceHelp(config));
                }).tooltip(paceHelp(config)).build());
                widgets.add(Button.builder(value("lens", config.lens), button -> {
                    config.lens = CinematicConfig.Lens.values()[(config.lens.ordinal() + 1) % CinematicConfig.Lens.values().length];
                    button.setMessage(value("lens", config.lens));
                }).tooltip(Tooltip.create(text("lens.help"))).build());
                widgets.add(Button.builder(text("shots", config.shots.size()), button -> {
                    page = Page.SHOTS;
                    offset = 0;
                    rebuildWidgets();
                }).tooltip(Tooltip.create(text("shots.help"))).build());
            }
            case DISPLAY -> {
                widgets.add(new AbstractSliderButton(0, 0, buttonWidth, 20, Component.empty(), (config.cinematicFps - 20) / 220.0) {
                    { updateMessage(); setTooltip(Tooltip.create(text("fps.help"))); }
                    @Override protected void updateMessage() { setMessage(text("fps", (int)Math.round(value * 220) + 20)); }
                    @Override protected void applyValue() { config.cinematicFps = (int)Math.round(value * 220) + 20; }
                    //? if >=1.21.9 {
                    @Override public boolean keyPressed(KeyEvent event) {
                        int key = event.key();
                    //?} else {
                    /*@Override public boolean keyPressed(int key, int scan, int modifiers) {
                    *///?}
                        int previous = config.cinematicFps;
                        //? if >=1.21.9 {
                        boolean handled = super.keyPressed(event);
                        //?} else {
                        /*boolean handled = super.keyPressed(key, scan, modifiers);
                        *///?}
                        if (handled && (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT)) {
                            config.cinematicFps = Math.clamp(previous + (key == InputConstants.KEY_RIGHT ? 1 : -1), 20, 240);
                            value = (config.cinematicFps - 20) / 220.0;
                            updateMessage();
                        }
                        return handled;
                    }
                });
                widgets.add(toggle("hideHud", () -> config.hideHud, value -> config.hideHud = value));
                widgets.add(toggle("hidePlayerNames", () -> config.hidePlayerNames, value -> config.hidePlayerNames = value));
                widgets.add(toggle("letterbox", () -> config.letterbox, value -> config.letterbox = value));
                widgets.add(toggle("music", () -> config.music, value -> config.music = value));
            }
            case SHOTS -> {
                widgets.add(Button.builder(text("shots.all"), button -> {
                    config.shots = EnumSet.allOf(Shot.Type.class);
                    rebuildWidgets();
                }).build());
                widgets.add(Button.builder(text("shots.moving"), button -> {
                    config.shots = EnumSet.complementOf(EnumSet.of(Shot.Type.PAN, Shot.Type.STILL));
                    rebuildWidgets();
                }).build());
                for (Shot.Type type : Shot.Type.values()) {
                    String key = "shot." + type.name().toLowerCase(Locale.ROOT);
                    Button button = toggle(key, () -> config.shots.contains(type), enabled -> {
                        if (enabled) config.shots.add(type);
                        else if (config.shots.size() > 1) config.shots.remove(type);
                        refreshShots(config);
                    });
                    shotButtons.put(type, button);
                    widgets.add(button);
                }
                refreshShots(config);
            }
        }
        return widgets;
    }

    private static Tooltip paceHelp(CinematicConfig config) {
        return Tooltip.create(text("pace.help", (int)config.pace.minimumSeconds, (int)config.pace.maximumSeconds));
    }

    private void refreshShots(CinematicConfig config) {
        shotButtons.forEach((type, button) -> {
            boolean last = config.shots.contains(type) && config.shots.size() == 1;
            button.active = !last && AfkCinematics.store().readable();
            button.setTooltip(Tooltip.create(text(last ? "shots.last" : "shot." + type.name().toLowerCase(Locale.ROOT) + ".help")));
        });
    }

    private Button toggle(String key, BooleanSupplier get, Consumer<Boolean> set) {
        Button button = Button.builder(label(key, get.getAsBoolean() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(label(key, get.getAsBoolean() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF));
        }).build();
        button.setTooltip(Tooltip.create(text(key + ".help")));
        return button;
    }

    private static Component value(String key, Enum<?> value) { return label(key, text(value.name().toLowerCase(Locale.ROOT))); }
    private static Component label(String key, Component value) { return Component.translatable("options.generic_value", text(key), value); }
    private static Component text(String key, Object... values) { return Component.translatable("ohneb_afk_cinematics." + key, values); }

    private boolean save() {
        if (!AfkCinematics.store().readable()) return true;
        if (AfkCinematics.store().save()) return true;
        error = text("save_error");
        saveFailed = true;
        close.setMessage(text("close_unsaved"));
        return false;
    }

    @Override public void onClose() { if (saveFailed || save()) AfkCinematics.showScreen(parent); }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (maxOffset == 0 || vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
        offset = Math.clamp(offset - (int)Math.signum(vertical), 0, maxOffset);
        rebuildWidgets();
        return true;
    }

    //? if >=1.21.9 {
    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
    //?} else {
    /*@Override public boolean keyPressed(int key, int scan, int modifiers) {
    *///?}
        if (maxOffset > 0 && (key == InputConstants.KEY_PAGEUP || key == InputConstants.KEY_PAGEDOWN)) {
            offset = Math.clamp(offset + (key == InputConstants.KEY_PAGEUP ? -1 : 1), 0, maxOffset);
            rebuildWidgets();
            return true;
        }
        //? if >=1.21.9 {
        return super.keyPressed(event);
        //?} else {
        /*return super.keyPressed(key, scan, modifiers);
        *///?}
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, page == Page.SHOTS ? text("shots.title") : title, width / 2, 10, 0xFFFFFFFF);
        Component status = error;
        if (!AfkCinematics.store().readable()) status = text("read_error");
        else if (!AfkCinematics.conflict().isEmpty()) status = text("conflict", AfkCinematics.conflict());
        else if (status.getString().isEmpty() && maxOffset > 0) status = text("scroll");
        if (!status.getString().isEmpty()) {
            var lines = font.split(status, width - 24);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                var line = lines.get(i);
                graphics.drawString(font, line, (width - font.width(line)) / 2, height - 49 + i * 10, 0xFFCCCCCC);
            }
        }
    }
}
