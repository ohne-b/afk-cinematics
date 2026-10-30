package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.core.Shot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.CycleButton;
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
import java.util.function.Function;

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
    private Button close, preview;
    private Component startIssue;

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
        preview = Button.builder(text("preview"), button -> {
            if (save()) { AfkCinematics.showScreen(null); AfkCinematics.requestStart(); }
        }).bounds(width / 2 - half - 3, height - 26, half, 20).build();
        startIssue = null;
        updateStart();
        addRenderableWidget(preview);
        close = addRenderableWidget(Button.builder(saveFailed ? text("close_unsaved") : CommonComponents.GUI_DONE, button -> onClose())
            .bounds(width / 2 + 3, height - 26, half, 20).build());
    }

    @Override public void tick() {
        super.tick();
        updateStart();
    }

    private void updateStart() {
        Component problem = AfkCinematics.startProblem();
        if (problem.equals(startIssue)) return;
        startIssue = problem;
        preview.active = problem.getString().isEmpty();
        preview.setTooltip(Tooltip.create(preview.active ? text("preview.help") : problem));
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
                widgets.add(choice("subject", config.subject, CinematicConfig.Subject.values(), value -> text(value.name().toLowerCase(Locale.ROOT)), value -> {
                    config.subject = value;
                    refreshControls();
                }, value -> Tooltip.create(text("subject.help"))));
                widgets.add(slider("playerShare", buttonWidth, 0, 100, config.playerShare, value -> config.playerShare = value));
                widgets.getLast().active = config.subject == CinematicConfig.Subject.MIXED;
                widgets.add(choice("motion", config.motion, CinematicConfig.Motion.values(), value -> text(value.name().toLowerCase(Locale.ROOT)),
                    value -> config.motion = value, value -> Tooltip.create(text("motion.help"))));
                widgets.add(choice("pace", config.pace, CinematicConfig.Pace.values(), value -> text(value.name().toLowerCase(Locale.ROOT)),
                    value -> config.pace = value, value -> Tooltip.create(text("pace.help", (int)value.minimumSeconds, (int)value.maximumSeconds))));
                widgets.add(choice("lens", config.lens, CinematicConfig.Lens.values(), value -> text(value.name().toLowerCase(Locale.ROOT)),
                    value -> config.lens = value, value -> Tooltip.create(text("lens.help"))));
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
                widgets.add(choice("soundtrack", config.soundtrack, CinematicConfig.Soundtrack.values(), value -> text("soundtrack." + value.name().toLowerCase(Locale.ROOT)), value -> {
                    config.soundtrack = value;
                    refreshControls();
                }, value -> Tooltip.create(text("soundtrack.help"))));
                widgets.add(slider("musicVolume", buttonWidth, 0, 100, config.musicVolume, value -> config.musicVolume = value));
                if (config.soundtrack == CinematicConfig.Soundtrack.CUSTOM) {
                    var input = new EditBox(font, buttonWidth, 20, text("customMusic"));
                    input.setMaxLength(256);
                    input.setValue(config.customMusic);
                    input.setHint(text("customMusic.hint"));
                    input.setTooltip(Tooltip.create(text("customMusic.help")));
                    input.setTextColor(CinematicMusic.customAvailable(config.customMusic) ? 0xFFFFFFFF : 0xFFFF7777);
                    input.setResponder(value -> {
                        config.customMusic = value.strip();
                        input.setTextColor(CinematicMusic.customAvailable(config.customMusic) ? 0xFFFFFFFF : 0xFFFF7777);
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
                if (shotSet == ShotSet.SCENERY) widgets.add(Button.builder(text("shots.moving"), button -> {
                    config.shots.addAll(types);
                    config.shots.remove(Shot.Type.PAN);
                    config.shots.remove(Shot.Type.STILL);
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

    private <T> CycleButton<T> choice(String key, T initial, T[] values, Function<T, Component> labels,
        Consumer<T> set, Function<T, Tooltip> help) {
        //? if >=1.21.11 {
        var builder = CycleButton.builder(labels, initial);
        //?} else {
        /*var builder = CycleButton.builder(labels).withInitialValue(initial);
        *///?}
        return builder.withValues(values).withTooltip(help::apply).create(text(key), (button, value) -> set.accept(value));
    }

    private CycleButton<CinematicConfig.StartMode> activity(String key, Supplier<CinematicConfig.StartMode> get, Consumer<CinematicConfig.StartMode> set) {
        return choice(key, get.get(), CinematicConfig.StartMode.values(), value -> startLabel(key, value), set,
            value -> Tooltip.create(text(key + ".help")));
    }

    private static Component startLabel(String key, CinematicConfig.StartMode mode) {
        return text("start." + (mode == CinematicConfig.StartMode.AUTOMATIC ? key.equals("fishing") ? "cast" : "idle" : mode.name().toLowerCase(Locale.ROOT)));
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
            if (key.equals("enabled")) refreshControls();
        }).build();
        button.setTooltip(Tooltip.create(text(key + ".help")));
        return button;
    }

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
        else if (status.getString().isEmpty() && (page == Page.HOME || page == Page.CONTROLS)) status = AfkCinematics.TOGGLE.isUnbound()
            ? text("key.unbound") : text("key.start", AfkCinematics.TOGGLE.getTranslatedKeyMessage());
        else if (status.getString().isEmpty() && (page == Page.CAMERA || page == Page.ACTIVITIES || page == Page.SOUND)) status = text("key.choices");
        if (!status.getString().isEmpty()) {
            var lines = font.split(status, width - 24);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                var line = lines.get(i);
                graphics.drawString(font, line, (width - font.width(line)) / 2, height - 49 + i * 10, 0xFFCCCCCC);
            }
        }
    }
}
