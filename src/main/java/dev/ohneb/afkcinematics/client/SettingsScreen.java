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
import java.util.function.Function;

public final class SettingsScreen extends Screen {
    private enum Page { HOME, SHOTS }
    private enum Section { CAMERA, AFK, MUSIC }
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
    private Section section;
    private int shotsOffset;
    private ShotSet shotSet = ShotSet.SCENERY;
    private Button shotSetControl;
    private int offset;
    private int maxOffset;
    private Component error = Component.empty();
    private boolean saveFailed, focusSaveError;
    private Button close, preview;
    private Component startIssue;
    private final List<AbstractWidget> navigation = new ArrayList<>();
    private final java.util.Map<EditBox, Component> numericLabels = new java.util.HashMap<>();
    private final java.util.Set<String> invalidNumbers = new java.util.HashSet<>();
    private final java.util.Map<String, String> numericDrafts = new java.util.HashMap<>();
    private String focusNumber;
    private int returnFocus;
    private int resumedFocus = -1;

    public SettingsScreen(Screen parent) {
        super(text("settings"));
        this.parent = parent;
        AfkCinematics.stop();
    }

    @Override protected void init() {
        CinematicConfig config = AfkCinematics.config();
        int columns = page == Page.SHOTS && width >= 320 ? 2 : 1;
        int contentWidth = Math.min(width - 32, 360);
        int buttonWidth = (contentWidth - (columns - 1) * 6) / columns;
        int x = (width - contentWidth) / 2;
        if (page != Page.HOME) addRenderableWidget(Button.builder(text("back"), ignored -> back())
            .bounds(x, 28, contentWidth, 20).build());
        shotButtons.clear();
        numericLabels.clear();
        List<AbstractWidget> widgets = controls(config, buttonWidth);
        navigation.clear();
        navigation.addAll(widgets);
        int top = page == Page.HOME ? 32 : 54;
        int visibleRows = Math.max(1, (height - top - 54) / 22);
        boolean pairs = page == Page.HOME && width >= 320;
        int rows = pairs ? widgets.size() - 3 : (widgets.size() + columns - 1) / columns;
        maxOffset = Math.max(0, rows - visibleRows);
        offset = Math.clamp(offset, 0, maxOffset);
        for (int index = 0; index < widgets.size(); index++) {
            int row = (pairs ? index < 6 ? index / 2 : index - 3 : index / columns) - offset;
            if (row < 0 || row >= visibleRows) continue;
            AbstractWidget widget = widgets.get(index);
            int rowColumns = pairs && index < 6 ? 2 : columns;
            int cellWidth = (contentWidth - (rowColumns - 1) * 6) / rowColumns;
            widget.setX(x + (index % rowColumns) * (cellWidth + 6));
            widget.setY(top + row * 22);
            widget.setWidth(numericLabels.containsKey(widget) ? 42 : cellWidth);
            widget.active &= AfkCinematics.store().readable();
            addRenderableWidget(widget);
        }
        if (maxOffset > 0) {
            int arrowHeight = visibleRows == 1 ? 10 : 20;
            var up = addRenderableWidget(Button.builder(Component.literal("↑"), ignored -> scroll(-1))
                .bounds(x + contentWidth + 2, top, 14, arrowHeight).build());
            var down = addRenderableWidget(Button.builder(Component.literal("↓"), ignored -> scroll(1))
                .bounds(x + contentWidth + 2, top + Math.max(12, (visibleRows - 1) * 22), 14, arrowHeight).build());
            up.active = offset > 0;
            down.active = offset < maxOffset;
        }
        int half = (contentWidth - 6) / 2;
        preview = Button.builder(text("preview"), button -> {
            if (save()) { AfkCinematics.showScreen(null); AfkCinematics.requestStart(); }
        }).bounds(width / 2 - half - 3, height - 26, half, 20).build();
        startIssue = null;
        updateStart();
        if (focusNumber != null) {
            numericLabels.forEach((input, label) -> {
                if (label.equals(text(focusNumber + ".input")) && children().contains(input)) {
                    setFocused(input);
                    input.setFocused(true);
                }
            });
            focusNumber = null;
        }
        addRenderableWidget(preview);
        close = addRenderableWidget(Button.builder(saveFailed ? text("close_unsaved") : CommonComponents.GUI_DONE, button -> onClose())
            .bounds(width / 2 + 3, height - 26, half, 20).build());
        if (resumedFocus >= 0 && resumedFocus < navigation.size() && children().contains(navigation.get(resumedFocus))) {
            setFocused(navigation.get(resumedFocus));
            navigation.get(resumedFocus).setFocused(true);
            resumedFocus = -1;
        }
    }

    @Override public void tick() {
        super.tick();
        updateStart();
        if (focusSaveError) {
            setFocused(close);
            close.setFocused(true);
            focusSaveError = false;
        }
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
                widgets.add(toggle("enabled", () -> config.enabled, value -> config.enabled = value));
                widgets.add(Button.builder(text("toggle.binding", AfkCinematics.TOGGLE.getTranslatedKeyMessage()), button -> {
                    if (save()) {
                        resumedFocus = navigation.indexOf(button);
                        AfkCinematics.showScreen(new KeyBindsScreen(this, minecraft.options));
                    }
                }).tooltip(Tooltip.create(text("bindings.help"))).build());
                widgets.add(toggle("automatic", () -> config.automatic, value -> config.automatic = value));
                var delay = number("delay", buttonWidth, 10, 600, config.idleSeconds, value -> config.idleSeconds = value);
                delay.active = config.automatic;
                widgets.add(delay);
                widgets.add(toggle("hideHud", () -> config.hideHud, value -> config.hideHud = value));
                widgets.add(toggle("hidePlayerNames", () -> config.hidePlayerNames, value -> config.hidePlayerNames = value));
                widgets.add(toggle("letterbox", () -> config.letterbox, value -> config.letterbox = value));
                widgets.add(expander(Section.CAMERA));
                if (section == Section.CAMERA) {
                    widgets.add(choice("motion", config.motion, CinematicConfig.Motion.values(), value -> text(value.name().toLowerCase(Locale.ROOT)),
                        value -> config.motion = value, value -> Tooltip.create(text("motion.help"))));
                    widgets.add(choice("pace", config.pace, CinematicConfig.Pace.values(), value -> text("pace.range", (int)value.minimumSeconds, (int)value.maximumSeconds),
                        value -> config.pace = value, value -> Tooltip.create(text("pace.help", (int)value.minimumSeconds, (int)value.maximumSeconds))));
                    widgets.add(choice("lens", config.lens, CinematicConfig.Lens.values(), value -> text(value.name().toLowerCase(Locale.ROOT)),
                        value -> config.lens = value, value -> Tooltip.create(text("lens.help"))));
                    widgets.add(Button.builder(text("shots", config.shots.size()), button -> openShots(shotSet, button))
                        .tooltip(Tooltip.create(text("shots.help"))).build());
                }
                widgets.add(expander(Section.AFK));
                if (section == Section.AFK) {
                    widgets.add(choice("subject", config.subject, CinematicConfig.Subject.values(), value -> text(value.name().toLowerCase(Locale.ROOT)), value -> {
                        config.subject = value;
                        refreshControls();
                    }, value -> Tooltip.create(text("subject.help"))));
                    if (config.subject == CinematicConfig.Subject.MIXED)
                        widgets.add(slider("playerShare", buttonWidth, 0, 100, config.playerShare, value -> config.playerShare = value));
                    var wildlife = toggle("wildlife", () -> config.wildlife, value -> config.wildlife = value);
                    wildlife.active = config.subject != CinematicConfig.Subject.PLAYER;
                    widgets.add(wildlife);
                    widgets.add(number("fps", buttonWidth, 20, 240, config.cinematicFps, value -> config.cinematicFps = value));
                    widgets.add(toggle("keepChat", () -> config.keepChat, value -> config.keepChat = value));
                    widgets.add(toggle("unfocused", () -> config.unfocused, value -> config.unfocused = value));
                }
                widgets.add(expander(Section.MUSIC));
                if (section != Section.MUSIC) break;
                widgets.add(toggle("music", () -> config.music, value -> config.music = value));
                if (!config.music) break;
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

    private EditBox number(String key, int width, int min, int max, int initial, java.util.function.IntConsumer set) {
        var input = new EditBox(font, width, 20, text(key + ".input"));
        numericLabels.put(input, text(key + ".input"));
        input.setMaxLength(3);
        input.setValue(numericDrafts.computeIfAbsent(key, ignored -> Integer.toString(initial)));
        input.setTextColor(invalidNumbers.contains(key) ? 0xFFFF7777 : 0xFFFFFFFF);
        input.setTooltip(Tooltip.create(text(key + ".help")));
        input.setResponder(value -> {
            numericDrafts.put(key, value);
            int number;
            try { number = Integer.parseInt(value); }
            catch (NumberFormatException ignored) { number = 0; }
            boolean valid = number >= min && number <= max;
            input.setTextColor(valid ? 0xFFFFFFFF : 0xFFFF7777);
            if (valid) {
                invalidNumbers.remove(key);
                if (invalidNumbers.isEmpty() && error.equals(text("number.invalid"))) error = Component.empty();
                set.accept(number);
            }
            else invalidNumbers.add(key);
        });
        return input;
    }

    private Button expander(Section value) {
        return Button.builder(text("section", section == value ? "−" : "+", text("section." + value.name().toLowerCase(Locale.ROOT))), button -> {
            section = section == value ? null : value;
            rebuildWidgets();
            for (int i = 0; i < navigation.size(); i++) {
                if (!navigation.get(i).getMessage().getString().endsWith("  " + text("section." + value.name().toLowerCase(Locale.ROOT)).getString())) continue;
                int row = width >= 320 ? i - 3 : i;
                if (row < offset) { offset = row; rebuildWidgets(); }
                break;
            }
            for (var child : children()) if (child instanceof Button candidate && candidate.getMessage().getString().endsWith(text("section." + value.name().toLowerCase(Locale.ROOT)).getString())) {
                setFocused(candidate);
                candidate.setFocused(true);
                break;
            }
        }).build();
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
        int focused = navigation.indexOf(getFocused());
        rebuildWidgets();
        if (focused >= 0 && focused < navigation.size()) {
            var widget = navigation.get(focused);
            if (!children().contains(widget)) return;
            setFocused(widget);
            widget.setFocused(true);
        }
    }

    private void openShots(ShotSet set, Button button) {
        returnFocus = navigation.indexOf(button);
        shotsOffset = offset;
        shotSet = set;
        open(Page.SHOTS);
    }

    private void back() {
        page = Page.HOME;
        offset = shotsOffset;
        rebuildWidgets();
        if (returnFocus >= 0 && returnFocus < navigation.size() && children().contains(navigation.get(returnFocus))) {
            setFocused(navigation.get(returnFocus));
            navigation.get(returnFocus).setFocused(true);
        }
    }

    private <T> CycleButton<T> choice(String key, T initial, T[] values, Function<T, Component> labels,
        Consumer<T> set, Function<T, Tooltip> help) {
        //? if >=1.21.11 {
        var builder = CycleButton.builder(labels, initial).withValues(values);
        //?} else {
        /*var builder = CycleButton.builder(labels).withValues(values).withInitialValue(initial);
        *///?}
        return builder.withTooltip(help::apply).create(text(key), (button, value) -> set.accept(value));
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
            if (key.equals("enabled") || key.equals("automatic") || key.equals("music")) refreshControls();
        }).build();
        button.setTooltip(Tooltip.create(text(key + ".help")));
        return button;
    }

    private static Component label(String key, Component value) { return Component.translatable("options.generic_value", text(key), value); }
    private static Component text(String key, Object... values) { return Component.translatable("ohneb_afk_cinematics." + key, values); }

    private boolean save() {
        if (!AfkCinematics.store().readable()) return true;
        if (!invalidNumbers.isEmpty()) {
            error = text("number.invalid");
            focusNumber = invalidNumbers.iterator().next();
            page = Page.HOME;
            if (focusNumber.equals("fps")) section = Section.AFK;
            rebuildWidgets();
            for (int i = 0; i < navigation.size(); i++) {
                if (!navigation.get(i).getMessage().equals(text(focusNumber + ".input"))) continue;
                int row = width >= 320 ? i < 6 ? i / 2 : i - 3 : i;
                offset = Math.clamp(row, 0, maxOffset);
                rebuildWidgets();
                break;
            }
            return false;
        }
        if (AfkCinematics.store().save()) return true;
        error = text("save_error");
        saveFailed = true;
        close.setMessage(text("close_unsaved"));
        close.setTooltip(Tooltip.create(error));
        focusSaveError = true;
        return false;
    }

    @Override public void onClose() { if (saveFailed || save()) AfkCinematics.showScreen(parent); }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (maxOffset == 0 || vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
        scroll(-(int)Math.signum(vertical));
        return true;
    }

    private void scroll(int rows) {
        offset = Math.clamp(offset + rows, 0, maxOffset);
        rebuildWidgets();
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
            ? text("shots.title", text("shots.set." + shotSet.name().toLowerCase(Locale.ROOT))) : title, width / 2, 10, 0xFFFFFFFF);
        // Text fields retain their label even while being edited.
        numericLabels.forEach((input, label) -> {
            if (children().contains(input)) graphics.drawString(font, label, input.getX() + 48, input.getY() + 6,
                input.active ? 0xFFCCCCCC : 0xFF777777);
        });
        Component status = error;
        if (!AfkCinematics.store().readable()) status = text("read_error");
        else if (!AfkCinematics.conflict().isEmpty()) status = text("conflict", AfkCinematics.conflict());
        else if (status.getString().isEmpty() && section == Section.MUSIC && AfkCinematics.config().music && AfkCinematics.config().soundtrack == CinematicConfig.Soundtrack.CUSTOM
            && !CinematicMusic.customAvailable(AfkCinematics.config().customMusic)) status = text("customMusic.missing");
        else if (status.getString().isEmpty() && maxOffset > 0) status = text("scroll");
        else if (status.getString().isEmpty() && page == Page.HOME) status = AfkCinematics.TOGGLE.isUnbound()
            ? text("key.unbound") : text("key.start", AfkCinematics.TOGGLE.getTranslatedKeyMessage());
        if (!status.getString().isEmpty()) {
            var lines = font.split(status, width - 24);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                var line = lines.get(i);
                graphics.drawString(font, line, (width - font.width(line)) / 2, height - 49 + i * 10, 0xFFCCCCCC);
            }
        }
    }
}
