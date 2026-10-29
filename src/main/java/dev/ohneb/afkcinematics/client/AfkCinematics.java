package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.core.*;
import dev.ohneb.afkcinematics.mixin.KeyMappingAccess;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Random;
import java.util.function.Predicate;

public final class AfkCinematics {
    public static final String ID = "ohneb_afk_cinematics";
    private static final Logger LOGGER = LoggerFactory.getLogger(ID);
    //? if >=1.21.9 {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.parse(ID + ":controls"));
    //?} else {
    /*private static final String CATEGORY = "key.categories.ohneb_afk_cinematics";
    *///?}
    public static final KeyMapping TOGGLE = new KeyMapping("key.ohneb_afk_cinematics.toggle", InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping SETTINGS = new KeyMapping("key.ohneb_afk_cinematics.settings", InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping NEXT = new KeyMapping("key.ohneb_afk_cinematics.next", InputConstants.UNKNOWN.getValue(), CATEGORY);
    private static final IdleTimer IDLE = new IdleTimer();
    private static final InputState INPUT = new InputState();
    private static final Random RANDOM = new Random();
    private static ConfigStore store;
    private static CinematicSession session;
    private static ClientLevel world;
    private static LocalPlayer player;
    private static ClientPacketListener connection;
    private static Vec3 anchor;
    private static Vec3 lastPosition;
    private static Pose vanillaPose;
    private static boolean focused;
    private static int manualWait;
    private static String conflict = "";
    private static long hintUntil;
    private static boolean pendingSettings;
    private static boolean manualFeedback;

    private AfkCinematics() { }

    public static void initialize(Path configDirectory, Predicate<String> installed) {
        store = new ConfigStore(configDirectory.resolve("ohneb-afk-cinematics.json"));
        for (String id : new String[]{"replaymod", "cameraoverhaul", "shouldersurfing", "betterthirdperson", "afk_cinematics", "afkcinematics"}) {
            if (installed.test(id)) { conflict = id; break; }
        }
        if (!store.readable()) LOGGER.warn(store.error());
        IDLE.reset(System.nanoTime());
        focused = false;
    }

    public static CinematicConfig config() { return store.config(); }
    public static ConfigStore store() { return store; }
    public static String conflict() { return conflict; }
    public static boolean ownsCamera() { return session != null; }
    public static boolean showing() { return session != null && session.showing(); }
    public static boolean hideHud() { return showing() && config().hideHud; }
    public static boolean showHint() {
        if (!showing() || fade() > 0 || System.nanoTime() >= hintUntil) return false;
        //? if >=26.2 {
        /*return !Minecraft.getInstance().gui.hud.isHidden();
        *///?} else {
        return !Minecraft.getInstance().options.hideGui;
        //?}
    }
    public static double fade() { return session == null ? 0 : session.fade(); }
    public static boolean scenery() { return session != null && session.scenery(); }
    public static void coveredFrame() { if (session != null) session.coveredFrame(); }

    public static void tick() {
        if (store == null) return;
        if (pendingSettings) { pendingSettings = false; openSettings(); }
        Minecraft client = Minecraft.getInstance();
        long now = System.nanoTime();
        if (client.level != world || client.player != player || client.getConnection() != connection) {
            stop();
            CinematicMusic.clear();
            INPUT.resetHeld();
            world = client.level;
            player = client.player;
            connection = client.getConnection();
            lastPosition = player == null ? null : player.position();
            vanillaPose = null;
        }
        boolean focus = client.isWindowActive();
        if (focus != focused) {
            INPUT.resetHeld();
            if (focus || !config().unfocused) stop();
            focused = focus;
        }
        boolean moved = player != null && lastPosition != null && player.position().distanceToSqr(lastPosition) > 0.0001;
        if (player != null) lastPosition = player.position();
        boolean eligible = eligible() && !moved;
        if (session != null) {
            if (!eligible || player.position().distanceToSqr(anchor) > 0.0001) stop();
            else {
                session.tick(now);
                if (session.ended()) {
                    if (manualFeedback) message("no_view");
                    stop();
                } else if (session.showing()) manualFeedback = false;
            }
            IDLE.reset(now);
        } else if (manualWait > 0) {
            manualWait--;
            IDLE.reset(now);
            if (eligible && vanillaPose != null) { manualWait = 0; start(true); }
            else if (manualWait == 0) message("unavailable");
        } else if (IDLE.ready(now, config().idleSeconds, eligible && config().automatic)) start(false);
        CinematicMusic.tick();
    }

    private static boolean eligible() {
        Minecraft client = Minecraft.getInstance();
        if (store == null || !config().enabled || !conflict.isEmpty() || player == null || world == null
            || client.player != player || client.level != world || client.getConnection() != connection
            || screen() != null || client.isPaused()
            || (!client.isWindowActive() && !config().unfocused) || INPUT.held()) return false;
        //? if >=26.2 {
        /*if (client.gui.overlay() != null) return false;
        *///?} else {
        if (client.getOverlay() != null) return false;
        //?}
        var options = client.options;
        if (options.keyUp.isDown() || options.keyDown.isDown() || options.keyLeft.isDown() || options.keyRight.isDown()
            || options.keyJump.isDown() || options.keyAttack.isDown() || options.keyUse.isDown() || options.keyDrop.isDown()) return false;
        return player.isAlive() && player.onGround() && !player.isSleeping() && !player.isPassenger()
            && !player.isFallFlying() && !player.isInWater() && !player.isInLava() && !player.isUsingItem()
            && !player.isOnFire() && player.hurtTime == 0 && !player.isSpectator();
    }

    private static void start(boolean manual) {
        if (!eligible() || vanillaPose == null) return;
        anchor = player.position();
        Vec subject = WorldGeometry.point(anchor.add(0, player.getBbHeight() * 0.65, 0));
        session = new CinematicSession(subject, vanillaPose, player.getYRot(), config(), new WorldGeometry(world), RANDOM);
        session.tick(System.nanoTime());
        hintUntil = 0;
        manualFeedback = manual && !session.showing();
    }

    public static void requestStart() {
        stop();
        if (store != null) manualWait = 20;
    }

    public static void stop() {
        session = null;
        anchor = null;
        manualWait = 0;
        manualFeedback = false;
        hintUntil = 0;
        IDLE.reset(System.nanoTime());
    }

    public static void nextShot() { if (session != null) session.nextShot(); }

    public static Pose cameraFrame(Camera camera) {
        if (session == null) {
            //? if >1.21.10 {
            vanillaPose = new Pose(WorldGeometry.point(camera.position()), camera.yRot(), camera.xRot());
            //?} else {
            /*vanillaPose = new Pose(WorldGeometry.point(camera.getPosition()), camera.getYRot(), camera.getXRot());
            *///?}
            return null;
        }
        if (!eligible() || player.position().distanceToSqr(anchor) > 0.0001) { stop(); return null; }
        long now = System.nanoTime();
        Pose pose = session.frame(now);
        if (session.ended()) stop();
        else if (session.settled() && hintUntil == 0) hintUntil = now + 4_000_000_000L;
        return pose;
    }

    public static boolean input(InputConstants.Key key, int action, boolean screenshot) {
        if (store == null) return false;
        long token = token(key);
        if (action == InputConstants.RELEASE) {
            IDLE.reset(System.nanoTime());
            // Focus loss can synthesize releases while the physical key is still held.
            if (!Minecraft.getInstance().isWindowActive() && INPUT.suppressed(token)) return true;
            if (INPUT.release(token)) {
                KeyMapping.set(key, false);
                for (KeyMapping binding : Minecraft.getInstance().options.keyMappings) {
                    if (matches(binding, key)) {
                        binding.setDown(false);
                        ((KeyMappingAccess)binding).afkCinematics$clicks(0);
                    }
                }
            }
            return false;
        }
        var options = Minecraft.getInstance().options;
        boolean dangerous = matches(options.keyAttack, key) || matches(options.keyUse, key) || matches(options.keyDrop, key);
        if (screenshot && action == InputConstants.PRESS) {
            // Vanilla consumes the press, but a shared gameplay binding can receive repeats.
            if (ownsCamera() && dangerous) INPUT.suppress(token);
            return false;
        }
        if (screenshot && !INPUT.suppressed(token)) return false;
        if (screen() == null && (matches(TOGGLE, key) || matches(SETTINGS, key) || matches(NEXT, key))) {
            INPUT.suppress(token);
            if (action == InputConstants.PRESS) {
                if (matches(SETTINGS, key)) openSettings();
                else if (matches(TOGGLE, key)) {
                    if (ownsCamera() || manualWait > 0) stop();
                    else requestStart();
                } else nextShot();
            }
            return true;
        }
        boolean suppressed = INPUT.press(token, ownsCamera() && dangerous);
        stop();
        return suppressed;
    }

    public static boolean matches(KeyMapping mapping, InputConstants.Key key) {
        return ((KeyMappingAccess) mapping).afkCinematics$key().equals(key);
    }

    public static boolean suppressed(InputConstants.Key key) { return INPUT != null && INPUT.suppressed(token(key)); }

    public static void blockInput(InputConstants.Key key) {
        INPUT.press(token(key), true);
        stop();
    }

    private static long token(InputConstants.Key key) {
        return ((long)key.getType().ordinal() << 32) | (key.getValue() & 0xffffffffL);
    }

    public static void mouseMoved(double x, double y) {
        if (Minecraft.getInstance().isWindowActive() && Math.abs(x) + Math.abs(y) > 0.01) stop();
    }

    public static void openSettings() {
        stop();
        showScreen(new SettingsScreen(screen()));
    }

    public static void scheduleSettings() { pendingSettings = true; }

    public static Screen screen() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.screen();
        *///?} else {
        return Minecraft.getInstance().screen;
        //?}
    }

    public static void showScreen(Screen screen) {
        //? if >=26.2 {
        /*Minecraft.getInstance().gui.setScreen(screen);
        *///?} else {
        Minecraft.getInstance().setScreen(screen);
        //?}
    }

    public static long window() {
        //? if >=1.21.9 {
        return Minecraft.getInstance().getWindow().handle();
        //?} else {
        /*return Minecraft.getInstance().getWindow().getWindow();
        *///?}
    }

    public static void message(String key) {
        if (Minecraft.getInstance().player == null) return;
        //? if >=26.2 {
        /*Minecraft.getInstance().gui.hud.setOverlayMessage(Component.translatable("ohneb_afk_cinematics." + key), false);
        *///?} else {
        Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("ohneb_afk_cinematics." + key), false);
        //?}
    }
}
