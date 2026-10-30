package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.core.Shot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
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
import java.util.function.Supplier;

public final class SettingsScreen extends Screen {
    private enum Page { HOME, GENERAL, ACTIVITIES, CAMERA, DISPLAY, SOUND, CONTROLS, SHOTS }
    private enum ShotSet {
        SCENERY, TRAVEL, FISHING;

        boolean includes(Shot.Type type) {
            return switch (this) {
                case SCENERY -> !type.travelOnly() && !type.fishingOnly();
                case TRAVEL -> type.travelOnly();
                case FISHING -> type.fishingOnly();
            };
        }
    }
    private final Screen parent;
    private final EnumMap<Shot.Type, Button> shotButtons = new EnumMap<>(Shot.Type.class);
    private Page page = Page.HOME;
    private ShotSet shotSet = ShotSet.SCENERY;
    private Button shotSetControl;
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
        if (page != Page.HOME) addRenderableWidget(Button.builder(text("back"), ignored -> back())
            .bounds(x, 28, contentWidth, 20).build());
        shotButtons.clear();
        List<AbstractWidget> widgets = controls(config, buttonWidth);
        int top = page == Page.HOME ? 32 : 54;
        int visibleRows = Math.max(1, (height - top - 54) / 22);
        maxOffset = Math.max(0, (widgets.size() + columns - 1) / columns - visibleRows);
        offset = Math.clamp(offset, 0, maxOffset);
        for (int index = 0; index < widgets.size(); index++) {
            int row = index / columns - offset;
            if (row < 0 || row >= visibleRows) continue;
            AbstractWidget widget = widgets.get(index);
            widget.setX(x + (index % columns) * (buttonWidth + 6));
            widget.setY(top + row * 22);
            widget.setWidth(buttonWidth);
            widget.active &= AfkCinematics.store().readable() || page == Page.HOME;
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
            case HOME -> {
                for (Page destination : new Page[]{Page.GENERAL, Page.ACTIVITIES, Page.CAMERA, Page.DISPLAY, Page.SOUND, Page.CONTROLS})
                    widgets.add(navigate(destination));
            }
            case GENERAL -> {
                widgets.add(toggle("enabled", () -> config.enabled, value -> config.enabled = value));
                widgets.add(toggle("automatic", () -> config.automatic, value -> config.automatic = value));
                widgets.add(slider("delay", buttonWidth, 10, 600, config.idleSeconds, value -> config.idleSeconds = value));
                widgets.add(toggle("unfocused", () -> config.unfocused, value -> config.unfocused = value));
            }
            case CAMERA -> {
                widgets.add(Button.builder(value("subject", config.subject), button -> {
                    config.subject = CinematicConfig.Subject.values()[(config.subject.ordinal() + 1) % CinematicConfig.Subject.values().length];
                    button.setMessage(value("subject", config.subject));
                    refreshControls();
                }).tooltip(Tooltip.create(text("subject.help"))).build());
                widgets.add(Button.builder(text("playerShare", config.playerShare), button -> {
                    config.playerShare = (config.playerShare / 25 + 1) % 5 * 25;
                    button.setMessage(text("playerShare", config.playerShare));
                }).tooltip(Tooltip.create(text("playerShare.help"))).build());
                widgets.getLast().active = config.subject == CinematicConfig.Subject.MIXED;
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
            case ACTIVITIES -> {
                widgets.add(activity("boats", () -> config.boats, value -> config.boats = value));
                widgets.add(activity("minecarts", () -> config.minecarts, value -> config.minecarts = value));
                widgets.add(activity("flight", () -> config.flight, value -> config.flight = value));
                widgets.add(activity("fishing", () -> config.fishing, value -> config.fishing = value));
            }
            case SOUND -> {
                widgets.add(toggle("music", () -> config.music, value -> config.music = value));
                widgets.add(Button.builder(label("soundtrack", text("soundtrack." + config.soundtrack.name().toLowerCase(Locale.ROOT))), button -> {
                    var choices = CinematicConfig.Soundtrack.values();
                    config.soundtrack = choices[(config.soundtrack.ordinal() + 1) % choices.length];
                    button.setMessage(label("soundtrack", text("soundtrack." + config.soundtrack.name().toLowerCase(Locale.ROOT))));
                    refreshControls();
                }).tooltip(Tooltip.create(text("soundtrack.help"))).build());
                widgets.add(slider("musicVolume", buttonWidth, 0, 100, config.musicVolume, value -> config.musicVolume = value));
                if (config.soundtrack == CinematicConfig.Soundtrack.CUSTOM) {
                    var input = new EditBox(font, buttonWidth, 20, text("customMusic"));
                    input.setMaxLength(256);
                    input.setValue(config.customMusic);
                    input.setHint(text("customMusic.hint"));
                    input.setTooltip(Tooltip.create(text("customMusic.help")));
                    input.setTextColor(CinematicMusic.customAvailable(config.customMusic) ? 0xFFFFFF : 0xFF7777);
                    input.setResponder(value -> {
                        config.customMusic = value.strip();
                        input.setTextColor(CinematicMusic.customAvailable(config.customMusic) ? 0xFFFFFF : 0xFF7777);
                    });
                    widgets.add(input);
                }
            }
            case CONTROLS -> {
                widgets.add(Button.builder(text("bindings"), button -> {
                    if (save()) AfkCinematics.showScreen(new KeyBindsScreen(this, minecraft.options));
                }).tooltip(Tooltip.create(text("bindings.help"))).build());
                widgets.add(toggle("travelControls", () -> config.travelControls, value -> config.travelControls = value));
                widgets.add(toggle("keepChat", () -> config.keepChat, value -> config.keepChat = value));
                widgets.add(toggle("manualLook", () -> config.manualLook, value -> config.manualLook = value));
            }
            case DISPLAY -> {
                widgets.add(slider("fps", buttonWidth, 20, 240, config.cinematicFps, value -> config.cinematicFps = value));
                widgets.add(toggle("hideHud", () -> config.hideHud, value -> config.hideHud = value));
                widgets.add(toggle("hidePlayerNames", () -> config.hidePlayerNames, value -> config.hidePlayerNames = value));
                widgets.add(toggle("letterbox", () -> config.letterbox, value -> config.letterbox = value));
            }
            case SHOTS -> {
                var types = EnumSet.noneOf(Shot.Type.class);
                for (Shot.Type type : Shot.Type.values()) if (shotSet.includes(type)) types.add(type);
                shotSetControl = Button.builder(label("shots.set", text("shots.set." + shotSet.name().toLowerCase(Locale.ROOT))), button -> {
                    boolean focused = button.isFocused();
                    shotSet = ShotSet.values()[(shotSet.ordinal() + 1) % ShotSet.values().length];
                    offset = 0;
                    rebuildWidgets();
                    if (focused) { setFocused(shotSetControl); shotSetControl.setFocused(true); }
                }).tooltip(Tooltip.create(text("shots.set.help"))).build();
                widgets.add(shotSetControl);
                widgets.add(Button.builder(text("shots.all"), button -> {
                    config.shots.addAll(types);
                    refreshControls();
                }).build());
                for (Shot.Type type : types) {
                    String key = "shot." + type.name().toLowerCase(Locale.ROOT);
                    Button button = toggle(key, () -> config.shots.contains(type), enabled -> {
                        if (enabled) config.shots.add(type);
                        else if (config.shots.size() > 1) config.shots.remove(type);
                        refreshShots(config);
                    });
                    shotButtons.put(type, button);
                    widgets.add(button);
                }
                if (shotSet == ShotSet.SCENERY) widgets.add(Button.builder(text("shots.moving"), button -> {
                    config.shots.addAll(types);
                    config.shots.remove(Shot.Type.PAN);
                    config.shots.remove(Shot.Type.STILL);
                    refreshControls();
                }).build());
                refreshShots(config);
            }
        }
        return widgets;
    }

    private AbstractSliderButton slider(String label, int width, int min, int max, int initial, java.util.function.IntConsumer set) {
        return new AbstractSliderButton(0, 0, width, 20, Component.empty(), (double)(initial - min) / (max - min)) {
            { updateMessage(); setTooltip(Tooltip.create(text(label + ".help"))); }
            @Override protected void updateMessage() { setMessage(text(label, number())); }
            private int number() { return (int)Math.round(value * (max - min)) + min; }
            @Override protected void applyValue() { set.accept(number()); }
            //? if >=1.21.9 {
            @Override public boolean keyPressed(KeyEvent event) {
                int key = event.key();
            //?} else {
            /*@Override public boolean keyPressed(int key, int scan, int modifiers) {
            *///?}
                int previous = number();
                //? if >=1.21.9 {
                boolean handled = super.keyPressed(event);
                //?} else {
                /*boolean handled = super.keyPressed(key, scan, modifiers);
                *///?}
                if (handled && (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT)) {
                    int number = Math.clamp(previous + (key == InputConstants.KEY_RIGHT ? 1 : -1), min, max);
                    value = (double)(number - min) / (max - min);
                    set.accept(number);
                    updateMessage();
                }
                return handled;
            }
        };
    }

    private void open(Page destination) {
        page = destination;
        offset = 0;
        rebuildWidgets();
    }

    private void refreshControls() {
        String focused = getFocused() instanceof AbstractWidget widget ? widget.getMessage().getString() : "";
        rebuildWidgets();
        for (var child : children()) if (child instanceof AbstractWidget widget && widget.active && widget.getMessage().getString().equals(focused)) {
            setFocused(widget);
            widget.setFocused(true);
            break;
        }
    }

    private void back() { open(page == Page.SHOTS ? Page.CAMERA : Page.HOME); }

    private Button navigate(Page destination) {
        String key = "page." + destination.name().toLowerCase(Locale.ROOT);
        return Button.builder(text(key), button -> open(destination)).tooltip(Tooltip.create(text(key + ".help"))).build();
    }

    private Button activity(String key, Supplier<CinematicConfig.StartMode> get, Consumer<CinematicConfig.StartMode> set) {
        return Button.builder(label(key, startLabel(key, get.get())), button -> {
            var modes = CinematicConfig.StartMode.values();
            set.accept(modes[(get.get().ordinal() + 1) % modes.length]);
            button.setMessage(label(key, startLabel(key, get.get())));
        }).tooltip(Tooltip.create(text(key + ".help"))).build();
    }

    private static Component startLabel(String key, CinematicConfig.StartMode mode) {
        return text("start." + (mode == CinematicConfig.StartMode.AUTOMATIC ? key.equals("fishing") ? "cast" : "idle" : mode.name().toLowerCase(Locale.ROOT)));
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
        if (key == InputConstants.KEY_ESCAPE && page != Page.HOME) { back(); return true; }
        //? if >=1.21.9 {
        return super.keyPressed(event);
        //?} else {
        /*return super.keyPressed(key, scan, modifiers);
        *///?}
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, page == Page.SHOTS
            ? text("shots.title", text("shots.set." + shotSet.name().toLowerCase(Locale.ROOT))) : page == Page.HOME ? title : text("page." + page.name().toLowerCase(Locale.ROOT)), width / 2, 10, 0xFFFFFFFF);
        Component status = error;
        if (!AfkCinematics.store().readable()) status = text("read_error");
        else if (!AfkCinematics.conflict().isEmpty()) status = text("conflict", AfkCinematics.conflict());
        else if (status.getString().isEmpty() && page == Page.SOUND && AfkCinematics.config().soundtrack == CinematicConfig.Soundtrack.CUSTOM
            && !CinematicMusic.customAvailable(AfkCinematics.config().customMusic)) status = text("customMusic.missing");
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
