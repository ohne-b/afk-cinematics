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
import net.minecraft.client.input.InputWithModifiers;
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
import java.util.function.Supplier;

public final class SettingsScreen extends Screen {
    private enum Page { GENERAL, AFK, CAMERA, SHOTS, MUSIC }
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
    private Page page = Page.GENERAL;
    private final EnumMap<Page, Integer> offsets = new EnumMap<>(Page.class);
    private boolean focusPage, keyboardFocus;
    private ShotSet shotSet = ShotSet.SCENERY;
    private AbstractWidget shotSetControl;
    private int offset;
    private int maxOffset;
    private Component error = Component.empty();
    private boolean saveFailed, focusSaveError;
    private Button close;
    private int panelLeft, panelWidth, contentLeft, contentWidth, rowHeight, visibleRows;
    private final List<AbstractWidget> navigation = new ArrayList<>();
    private final List<List<AbstractWidget>> rows = new ArrayList<>();
    private final java.util.Map<AbstractWidget, Integer> labelPositions = new java.util.HashMap<>();
    private final java.util.Map<EditBox, Component> numericLabels = new java.util.HashMap<>();
    private final java.util.Map<AbstractWidget, Component> labels = new java.util.HashMap<>();
    private final java.util.Set<String> invalidNumbers = new java.util.HashSet<>();
    private final java.util.Map<String, String> numericDrafts = new java.util.HashMap<>();
    private String focusNumber;
    private int resumedFocus = -1;

    public SettingsScreen(Screen parent) {
        super(text("settings"));
        this.parent = parent;
        AfkCinematics.stop();
    }

    @Override protected void init() {
        CinematicConfig config = AfkCinematics.config();
        int totalWidth = Math.min(width - 24, 440);
        int left = (width - totalWidth) / 2;
        int sidebar = width >= 360 ? 64 : 52;
        int gap = width >= 360 ? 16 : 10;
        int x = left + sidebar + gap;
        int buttonWidth = totalWidth - sidebar - gap - 8;
        int top = 32;
        panelLeft = left;
        panelWidth = totalWidth;
        contentLeft = x;
        contentWidth = buttonWidth;
        rowHeight = buttonWidth < 210 ? 26 : 20;
        int tabHeight = Math.min(20, Math.max(10, (height - 62 - 12) / Page.values().length));
        for (Page destination : Page.values()) {
            var tab = new FlatButton(text("page." + destination.name().toLowerCase(Locale.ROOT)), ignored -> open(destination));
            tab.tab = true;
            tab.setX(left);
            tab.setY(top + destination.ordinal() * (tabHeight + 3));
            tab.setWidth(sidebar);
            tab.setHeight(tabHeight);
            tab.active = page != destination;
            addRenderableWidget(tab);
        }
        shotButtons.clear();
        numericLabels.clear();
        labels.clear();
        List<AbstractWidget> widgets = controls(config, buttonWidth);
        navigation.clear();
        navigation.addAll(widgets);
        rows.clear();
        if (page == Page.SHOTS) {
            rows.add(List.of(widgets.getFirst()));
            int presets = shotSet == ShotSet.SCENERY ? 3 : 2;
            if (buttonWidth >= 210) rows.add(widgets.subList(1, presets));
            else for (int i = 1; i < presets; i++) rows.add(List.of(widgets.get(i)));
            int columns = buttonWidth >= 280 ? 2 : 1;
            for (int i = presets; i < widgets.size(); i += columns) rows.add(widgets.subList(i, Math.min(i + columns, widgets.size())));
        } else widgets.forEach(widget -> rows.add(List.of(widget)));
        visibleRows = Math.max(1, (height - top - 46) / rowHeight);
        maxOffset = Math.max(0, rows.size() - visibleRows);
        offset = Math.clamp(offset, 0, maxOffset);
        labelPositions.clear();
        for (int index = offset; index < Math.min(rows.size(), offset + visibleRows); index++) {
            var row = rows.get(index);
            int columnWidth = (buttonWidth - 12 * (row.size() - 1)) / row.size();
            for (int column = 0; column < row.size(); column++) {
                AbstractWidget widget = row.get(column);
                int columnX = x + column * (columnWidth + 12);
                int controlWidth = labels.containsKey(widget) ? Math.min(116, Math.max(54, columnWidth / 2)) : columnWidth;
                if (widget instanceof FlatButton flat && flat.state != null) controlWidth = 42;
                if (numericLabels.containsKey(widget)) controlWidth = 48;
                boolean fullLabel = widget instanceof FlatButton flat && flat.state == null && buttonWidth < 210;
                if (fullLabel) { controlWidth = columnWidth; ((FlatButton)widget).fullLabel = true; }
                widget.setX(columnX + columnWidth - controlWidth);
                int controlHeight = fullLabel ? 24 : 18;
                widget.setY(top + (index - offset) * rowHeight + (rowHeight - controlHeight) / 2);
                widget.setWidth(widget instanceof AbstractSliderButton ? controlWidth - font.width("100%") - 8 : controlWidth);
                widget.setHeight(controlHeight);
                widget.active &= AfkCinematics.store().readable();
                labelPositions.put(widget, columnX);
                addRenderableWidget(widget);
            }
        }
        if (maxOffset > 0) {
            var up = new FlatButton(text("scroll.up"), ignored -> scroll(-1));
            var down = new FlatButton(text("scroll.down"), ignored -> scroll(1));
            up.display = () -> Component.literal("\u2191");
            down.display = () -> Component.literal("\u2193");
            for (var arrow : new FlatButton[]{up, down}) {
                arrow.setX(left + totalWidth - 6);
                arrow.setWidth(10);
                arrow.setHeight(10);
                addRenderableWidget(arrow);
            }
            up.setY(top);
            down.setY(top + visibleRows * rowHeight - 10);
            up.active = offset > 0;
            down.active = offset < maxOffset;
        }
        close = new FlatButton(saveFailed ? text("close_unsaved") : CommonComponents.GUI_DONE, button -> onClose());
        close.setWidth(saveFailed ? Math.min(140, totalWidth) : 72);
        close.setHeight(18);
        close.setX(left + totalWidth - close.getWidth());
        close.setY(height - 24);
        addRenderableWidget(close);
        if (resumedFocus >= 0 && resumedFocus < navigation.size() && children().contains(navigation.get(resumedFocus))) {
            setFocused(navigation.get(resumedFocus));
            navigation.get(resumedFocus).setFocused(true);
            resumedFocus = -1;
        }
    }

    @Override public void tick() {
        super.tick();
        if (focusPage) {
            navigation.stream().filter(widget -> widget.active && children().contains(widget)).findFirst().ifPresent(widget -> {
                setFocused(widget);
                widget.setFocused(true);
            });
            focusPage = false;
        }
        if (focusNumber != null) {
            numericLabels.forEach((input, label) -> {
                if (label.equals(text(focusNumber + ".input")) && children().contains(input)) {
                    setFocused(input);
                    input.setFocused(true);
                }
            });
            focusNumber = null;
        }
        if (focusSaveError) {
            setFocused(close);
            close.setFocused(true);
            focusSaveError = false;
        }
    }

    private List<AbstractWidget> controls(CinematicConfig config, int buttonWidth) {
        List<AbstractWidget> widgets = new ArrayList<>();
        switch (page) {
            case GENERAL -> {
                widgets.add(toggle("enabled", () -> config.enabled, value -> config.enabled = value));
                var binding = new FlatButton(text("toggle.binding", AfkCinematics.TOGGLE.getTranslatedKeyMessage()), button -> {
                    if (save()) {
                        resumedFocus = navigation.indexOf(button);
                        AfkCinematics.showScreen(new KeyBindsScreen(this, minecraft.options));
                    }
                });
                binding.display = () -> AfkCinematics.TOGGLE.getTranslatedKeyMessage().copy().append("...");
                binding.setTooltip(Tooltip.create(text("bindings.help")));
                labels.put(binding, text("toggle.label"));
                widgets.add(binding);
                widgets.add(toggle("frontTravel", () -> config.frontTravel, value -> config.frontTravel = value));
                widgets.add(toggle("autoFishing", () -> config.autoFishing, value -> config.autoFishing = value));
                widgets.add(toggle("autoSleep", () -> config.autoSleep, value -> config.autoSleep = value));
                widgets.add(toggle("hideHud", () -> config.hideHud, value -> config.hideHud = value));
                widgets.add(toggle("hidePlayerNames", () -> config.hidePlayerNames, value -> config.hidePlayerNames = value));
                widgets.add(toggle("letterbox", () -> config.letterbox, value -> config.letterbox = value));
            }
            case AFK -> {
                widgets.add(toggle("automatic", () -> config.automatic, value -> config.automatic = value));
                widgets.add(number("delay", buttonWidth, 10, 600, config.idleSeconds, value -> config.idleSeconds = value));
                widgets.add(choice("subject", config.subject, CinematicConfig.Subject.values(), value -> text(value.name().toLowerCase(Locale.ROOT)), value -> {
                    config.subject = value;
                    refreshControls();
                }, value -> Tooltip.create(text("subject.help"))));
                var share = slider("playerShare", buttonWidth, 0, 100, config.playerShare, value -> config.playerShare = value);
                share.active = config.subject == CinematicConfig.Subject.MIXED;
                widgets.add(share);
                var wildlife = toggle("wildlife", () -> config.wildlife, value -> config.wildlife = value);
                wildlife.active = config.subject != CinematicConfig.Subject.PLAYER;
                widgets.add(wildlife);
                widgets.add(number("fps", buttonWidth, 20, 240, config.cinematicFps, value -> config.cinematicFps = value));
                widgets.add(toggle("keepChat", () -> config.keepChat, value -> config.keepChat = value));
                widgets.add(toggle("unfocused", () -> config.unfocused, value -> config.unfocused = value));
            }
            case CAMERA -> {
                widgets.add(choice("transition", config.transition, CinematicConfig.Transition.values(), value -> text("transition." + value.name().toLowerCase(Locale.ROOT)),
                    value -> config.transition = value, value -> Tooltip.create(text("transition.help"))));
                widgets.add(choice("motion", config.motion, CinematicConfig.Motion.values(), value -> text(value.name().toLowerCase(Locale.ROOT)),
                    value -> config.motion = value, value -> Tooltip.create(text("motion.help"))));
                widgets.add(choice("pace", config.pace, CinematicConfig.Pace.values(), value -> text("pace.range", (int)value.minimumSeconds, (int)value.maximumSeconds),
                    value -> config.pace = value, value -> Tooltip.create(text("pace.help", (int)value.minimumSeconds, (int)value.maximumSeconds))));
                widgets.add(toggle("useGameFov", () -> config.useGameFov, value -> {
                    config.useGameFov = value;
                    refreshControls();
                }));
                var fov = slider("customFov", buttonWidth, 30, 140, config.customFov, value -> config.customFov = value,
                    value -> Component.literal(value + "\u00B0"));
                fov.active = !config.useGameFov;
                widgets.add(fov);
            }
            case MUSIC -> {
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
                    labels.put(input, text("customMusic"));
                }
            }
            case SHOTS -> {
                var types = EnumSet.noneOf(Shot.Type.class);
                for (Shot.Type type : Shot.Type.values()) if (shotSet.includes(type)) types.add(type);
                shotSetControl = choice("shots.set", shotSet, ShotSet.values(),
                    value -> text("shots.set." + value.name().toLowerCase(Locale.ROOT)), value -> {
                        shotSet = value;
                        offset = 0;
                        resumedFocus = 0;
                        rebuildWidgets();
                    }, value -> Tooltip.create(text("shots.set.help")));
                widgets.add(shotSetControl);
                widgets.add(new FlatButton(text("shots.all"), button -> {
                    config.shots.addAll(types);
                    refreshControls();
                }));
                if (shotSet == ShotSet.SCENERY) widgets.add(new FlatButton(text("shots.moving"), button -> {
                    config.shots.addAll(types);
                    config.shots.remove(Shot.Type.PAN);
                    config.shots.remove(Shot.Type.STILL);
                    refreshControls();
                }));
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
        labels.put(input, text(key + ".input"));
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

    private AbstractSliderButton slider(String label, int width, int min, int max, int initial, java.util.function.IntConsumer set) {
        return slider(label, width, min, max, initial, set, value -> Component.literal(value + "%"));
    }

    private AbstractSliderButton slider(String label, int width, int min, int max, int initial,
                                        java.util.function.IntConsumer set, java.util.function.IntFunction<Component> display) {
        var slider = new AbstractSliderButton(0, 0, width, 18, Component.empty(), (double)(initial - min) / (max - min)) {
            { updateMessage(); setTooltip(Tooltip.create(text(label + ".help"))); }
            @Override protected void updateMessage() { setMessage(text(label, number())); }
            private int number() { return (int)Math.round(value * (max - min)) + min; }
            @Override protected void applyValue() { set.accept(number()); }
            //? if >=26.1 {
            /*@Override public void extractWidgetRenderState(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            *///?} else {
            @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            //?}
                int x = getX(), y = getY(), handle = x + (int)(value * (getWidth() - 8));
                graphics.fill(x, y + 8, x + getWidth(), y + 10, 0xFF39434A);
                graphics.fill(x, y + 8, handle + 4, y + 10, active ? 0xFF84D5BB : 0xFF66736D);
                graphics.fill(handle, y + 5, handle + 8, y + 13, isHovered() || isFocused() && keyboardFocus ? 0xFF84D5BB : 0xFFB5C4BE);
                graphics.drawString(font, display.apply(number()), x + getWidth() + 8, y + 5, active ? 0xFFE6ECE9 : 0xFF7E8983);
            }
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
        labels.put(slider, text(label + ".label"));
        return slider;
    }

    private void open(Page destination) {
        offsets.put(page, offset);
        page = destination;
        offset = offsets.getOrDefault(page, 0);
        focusPage = true;
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

    private <T> ChoiceButton<T> choice(String key, T initial, T[] values, Function<T, Component> labels,
        Consumer<T> set, Function<T, Tooltip> help) {
        var button = new ChoiceButton<>(key, initial, values, labels, set, help);
        this.labels.put(button, text(key));
        return button;
    }

    private void refreshShots(CinematicConfig config) {
        shotButtons.forEach((type, button) -> {
            boolean last = config.shots.contains(type) && config.shots.size() == 1;
            boolean front = type == Shot.Type.LEAD && !config.frontTravel;
            button.active = !last && !front && AfkCinematics.store().readable();
            button.setTooltip(Tooltip.create(text(front ? "shots.front_off" : last ? "shots.last" : "shot." + type.name().toLowerCase(Locale.ROOT) + ".help")));
        });
    }

    private Button toggle(String key, BooleanSupplier get, Consumer<Boolean> set) {
        FlatButton button = new FlatButton(label(key, get.getAsBoolean() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(label(key, get.getAsBoolean() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF));
            if (key.equals("enabled") || key.equals("automatic") || key.equals("music")) refreshControls();
        });
        button.state = get;
        button.display = () -> get.getAsBoolean() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
        button.setTooltip(Tooltip.create(text(key + ".help")));
        labels.put(button, text(key));
        return button;
    }

    private class FlatButton extends Button {
        protected Supplier<Component> display = this::getMessage;
        private BooleanSupplier state;
        private boolean tab, fullLabel;

        FlatButton(Component message, OnPress action) { super(0, 0, 100, 18, message, action, DEFAULT_NARRATION); }

        //? if >=26.1 {
        /*@Override protected void extractContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        *///?} else if >=1.21.11 {
        @Override protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        //?} else {
        /*@Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        *///?}
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean chosen = tab && !active || state != null && state.getAsBoolean();
            boolean highlighted = isHovered() || isFocused() && keyboardFocus;
            int background = chosen && (active || tab) ? 0xFF29493F : highlighted && active ? 0xFF364148 : 0xFF252C31;
            if (!tab || chosen || highlighted) graphics.fill(x, y, x + w, y + h, background);
            if (tab && chosen) graphics.fill(x, y + 3, x + 2, y + h - 3, 0xFF84D5BB);
            if (isFocused() && active && keyboardFocus) graphics.fill(x, y + 3, x + 2, y + h - 3, 0xFF84D5BB);
            if (fullLabel) {
                var lines = font.split(getMessage(), w - 8);
                int count = Math.min(2, lines.size());
                for (int i = 0; i < count; i++) graphics.drawString(font, lines.get(i), x + (w - font.width(lines.get(i))) / 2,
                    y + (h - count * 9) / 2 + i * 9, active ? 0xFFE4ECE8 : 0xFF7E8983);
                return;
            }
            Component value = display.get();
            var clipped = font.substrByWidth(value, w - (w > 14 ? 8 : 0)).getString();
            graphics.drawString(font, clipped, tab ? x + 7 : x + (w - font.width(clipped)) / 2, y + (h - 8) / 2,
                active || tab && chosen ? 0xFFE4ECE8 : 0xFF7E8983);
        }
    }

    private final class ChoiceButton<T> extends FlatButton {
        private final String key;
        private final T[] values;
        private final Function<T, Component> names;
        private final Consumer<T> change;
        private final Function<T, Tooltip> help;
        private int index;

        ChoiceButton(String key, T initial, T[] values, Function<T, Component> names, Consumer<T> change, Function<T, Tooltip> help) {
            super(label(key, names.apply(initial)), ignored -> { });
            this.key = key;
            this.values = values;
            this.names = names;
            this.change = change;
            this.help = help;
            index = java.util.Arrays.asList(values).indexOf(initial);
            display = () -> names.apply(values[index]).copy().append("  >");
            setTooltip(help.apply(initial));
        }

        private void cycle(int direction) {
            index = Math.floorMod(index + direction, values.length);
            T value = values[index];
            setMessage(label(key, names.apply(value)));
            setTooltip(help.apply(value));
            change.accept(value);
        }

        //? if >=1.21.9 {
        @Override public void onPress(InputWithModifiers input) { cycle(input.hasShiftDown() ? -1 : 1); }
        //?} else {
        /*@Override public void onPress() { cycle(Screen.hasShiftDown() ? -1 : 1); }
        *///?}

        //? if >=1.21.9 {
        @Override public boolean keyPressed(KeyEvent event) {
            int key = event.key();
        //?} else {
        /*@Override public boolean keyPressed(int key, int scan, int modifiers) {
        *///?}
            if (active && isFocused() && (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT)) {
                cycle(key == InputConstants.KEY_RIGHT ? 1 : -1);
                return true;
            }
            //? if >=1.21.9 {
            return super.keyPressed(event);
            //?} else {
            /*return super.keyPressed(key, scan, modifiers);
            *///?}
        }
    }

    private static Component label(String key, Component value) { return Component.translatable("options.generic_value", text(key), value); }
    private static Component text(String key, Object... values) { return Component.translatable("ohneb_afk_cinematics." + key, values); }

    private boolean save() {
        if (!AfkCinematics.store().readable()) return true;
        if (!invalidNumbers.isEmpty()) {
            error = text("number.invalid");
            focusNumber = invalidNumbers.iterator().next();
            offsets.put(page, offset);
            page = Page.AFK;
            rebuildWidgets();
            for (int i = 0; i < navigation.size(); i++) {
                if (!navigation.get(i).getMessage().equals(text(focusNumber + ".input"))) continue;
                offset = Math.clamp(i, 0, maxOffset);
                rebuildWidgets();
                break;
            }
            return false;
        }
        if (AfkCinematics.store().save()) return true;
        error = text("save_error");
        saveFailed = true;
        close.setMessage(text("close_unsaved"));
        close.setWidth(Math.min(140, panelWidth));
        close.setX(panelLeft + panelWidth - close.getWidth());
        close.setTooltip(Tooltip.create(error));
        focusSaveError = true;
        return false;
    }

    @Override public void onClose() { if (saveFailed || save()) AfkCinematics.showScreen(parent); }

    //? if >=1.21.9 {
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
    //?} else {
    /*@Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
    *///?}
        keyboardFocus = false;
        var before = List.copyOf(navigation);
        //? if >=1.21.9 {
        boolean handled = super.mouseClicked(event, doubleClick);
        //?} else {
        /*boolean handled = super.mouseClicked(mouseX, mouseY, button);
        *///?}
        // Vanilla focuses the clicked instance after its callback; a rebuild has replaced it by then.
        if (AfkCinematics.screen() == this && getFocused() != null && !children().contains(getFocused())) {
            int index = before.indexOf(getFocused());
            if (index >= 0 && index < navigation.size() && children().contains(navigation.get(index))) {
                setFocused(navigation.get(index));
                navigation.get(index).setFocused(true);
            } else if (getFocused() instanceof AbstractWidget old) {
                children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                    .filter(widget -> widget.active && widget.getMessage().equals(old.getMessage())).findFirst()
                    .ifPresentOrElse(widget -> { setFocused(widget); widget.setFocused(true); }, () -> focusPage = true);
            }
        }
        return handled;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        keyboardFocus = false;
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
        keyboardFocus = true;
        if (maxOffset > 0 && (key == InputConstants.KEY_PAGEUP || key == InputConstants.KEY_PAGEDOWN)) {
            offset = Math.clamp(offset + (key == InputConstants.KEY_PAGEUP ? -visibleRows : visibleRows), 0, maxOffset);
            focusPage = true;
            rebuildWidgets();
            return true;
        }
        //? if >=1.21.9 {
        return super.keyPressed(event);
        //?} else {
        /*return super.keyPressed(key, scan, modifiers);
        *///?}
    }

    //? if <=1.21.5 {
    /*@Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics, mouseX, mouseY, delta);
        drawPanel(graphics);
    }
    *///?}

    private void drawPanel(GuiGraphics graphics) {
        graphics.fill(panelLeft - 6, 26, panelLeft + panelWidth + 6, height - 29, 0xED171D21);
        graphics.fill(contentLeft - 9, 32, contentLeft - 8, height - 36, 0xFF303B41);
        for (int index = offset; index < Math.min(rows.size(), offset + visibleRows); index++) {
            int rowY = 32 + (index - offset) * rowHeight;
            graphics.fill(contentLeft, rowY + rowHeight - 1, contentLeft + contentWidth, rowY + rowHeight, 0xFF252E33);
            for (var widget : rows.get(index)) {
                Component label = labels.get(widget);
                if (label == null || widget instanceof FlatButton flat && flat.fullLabel) continue;
                int labelX = labelPositions.get(widget);
                var lines = font.split(label, Math.max(20, widget.getX() - labelX - 8));
                int count = Math.min(2, lines.size());
                for (int i = 0; i < count; i++) graphics.drawString(font, lines.get(i), labelX,
                    rowY + (rowHeight - count * 9) / 2 + i * 9, widget.active ? 0xFFDCE4E0 : 0xFF78857D);
            }
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Older Screen.render calls our background override before drawing its children.
        //? if >1.21.5 {
        drawPanel(graphics);
        //?}
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawString(font, title, panelLeft, 12, 0xFFFFFFFF);
        Component status = error;
        if (!AfkCinematics.store().readable()) status = text("read_error");
        else if (!AfkCinematics.conflict().isEmpty()) status = text("conflict", AfkCinematics.conflict());
        else if (status.getString().isEmpty() && page == Page.MUSIC && AfkCinematics.config().music && AfkCinematics.config().soundtrack == CinematicConfig.Soundtrack.CUSTOM
            && !CinematicMusic.customAvailable(AfkCinematics.config().customMusic)) status = text("customMusic.missing");
        if (!status.getString().isEmpty()) {
            var lines = font.split(status, panelWidth);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                var line = lines.get(i);
                graphics.drawString(font, line, panelLeft, height - 45 + i * 10, 0xFFE0B98E);
            }
        }
        if (maxOffset > 0) {
            int top = 45, space = Math.max(1, visibleRows * rowHeight - 26);
            int thumb = Math.max(4, space * visibleRows / rows.size());
            int y = top + (space - thumb) * offset / maxOffset;
            graphics.fill(panelLeft + panelWidth - 3, top, panelLeft + panelWidth - 1, top + space, 0xFF303B41);
            graphics.fill(panelLeft + panelWidth - 3, y, panelLeft + panelWidth - 1, y + thumb, 0xFF84A498);
        }
        if (page == Page.GENERAL && width >= 360) graphics.drawString(font,
            AfkCinematics.TOGGLE.isUnbound() ? text("key.unbound") : text("key.start", AfkCinematics.TOGGLE.getTranslatedKeyMessage()),
            panelLeft, height - 19, 0xFFAFBDB5);
    }
}
