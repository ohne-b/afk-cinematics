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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
//? if >=1.21.11 {
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
//?} else if >=1.21.2 {
/*import net.minecraft.world.entity.vehicle.AbstractBoat;
*///?} else {
/*import net.minecraft.world.entity.vehicle.Boat;
*///?}
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static MovingGeometry movingGeometry;
    private static Scene.Kind activity = Scene.Kind.PLAYER;
    private static Entity vehicle;
    private static double carriedYaw;
    private static Entity lastVehicle;
    private static Vec3 lastVehiclePosition;
    private static Vec3 observedMotion = Vec3.ZERO;
    private static double course;
    private static boolean manualTravel;
    private static FishingHook fishingHook;
    private static FishingHook lastFishingStart;
    private static InputConstants.Key activityUse;
    private static ClientLevel world;
    private static LocalPlayer player;
    private static ClientPacketListener connection;
    private static Vec3 anchor;
    private static Vec3 lastPosition;
    private static float lastYaw, lastPitch;
    private static Pose vanillaPose;
    private static Camera appliedCamera;
    private static boolean audioReady, cameraUnderwater;
    private static Vec3 audioPosition;
    private static FluidState audioFluid;
    private static boolean audioSubmerged;
    private static boolean focused;
    private static int manualWait;
    private static String conflict = "";
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
    public static boolean hidePlayerNames() { return showing() && config().hidePlayerNames; }
    public static double fade() { return session == null ? 0 : session.fade(); }
    public static boolean scenery() { return session != null && session.scenery(); }
    public static boolean underwater() { return session != null && session.underwater(); }
    public static void cameraApplied(Camera camera) {
        if (session != null && camera == appliedCamera) {
            cameraUnderwater = camera.getFluidInCamera() == net.minecraft.world.level.material.FogType.WATER;
            //? if >=1.21.11 {
            audioPosition = camera.position();
            //?} else {
            /*audioPosition = camera.getPosition();
            *///?}
            var block = BlockPos.containing(audioPosition);
            audioFluid = world.getFluidState(block);
            //? if >=26.3 {
            /*audioSubmerged = audioPosition.y < block.getY() + audioFluid.getHeightForCamera(world, block);
            *///?} else {
            audioSubmerged = audioPosition.y < block.getY() + audioFluid.getHeight(world, block);
            //?}
            audioReady = true;
        }
    }
    public static boolean cameraAudioActive(LocalPlayer source) {
        var client = Minecraft.getInstance();
        return session != null && audioReady && source == player && client.player == player
            && client.level == world && client.getConnection() == connection;
    }
    public static boolean audioUnderwater(LocalPlayer source, boolean normal) {
        return cameraAudioActive(source) ? cameraUnderwater : normal;
    }
    public static Vec3 ambientPosition(Player source) {
        return source == player && cameraAudioActive(player) ? audioPosition : source.getEyePosition();
    }
    public static boolean ambientFluid(Player source, TagKey<Fluid> tag) {
        return source == player && cameraAudioActive(player) ? audioSubmerged && audioFluid.is(tag) : source.isEyeInFluid(tag);
    }
    public static int frameLimit() {
        if (!showing() || !eligible()) return 0;
        var client = Minecraft.getInstance();
        //? if >=1.21.5 {
        if (client.getFramerateLimitTracker().getThrottleReason()
            == com.mojang.blaze3d.platform.FramerateLimitTracker.FramerateThrottleReason.WINDOW_ICONIFIED) return 0;
        //?} else if >=1.21.2 {
        /*if (client.getWindow().isIconified()) return 0;
        *///?} else {
        /*if (org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window(), org.lwjgl.glfw.GLFW.GLFW_ICONIFIED) == org.lwjgl.glfw.GLFW.GLFW_TRUE) return 0;
        *///?}
        return Math.clamp(config().cinematicFps, 20, 240);
    }
    public static double fieldOfView(Camera camera, double normal) {
        if (session == null || camera != appliedCamera) return normal;
        var window = Minecraft.getInstance().getWindow();
        return config().lens.blend(normal, (double)window.getWidth() / Math.max(1, window.getHeight()), session.lensStrength());
    }
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
            lastYaw = player == null ? 0 : player.getYRot();
            lastPitch = player == null ? 0 : player.getXRot();
            carriedYaw = 0;
            lastVehicle = null;
            lastVehiclePosition = null;
            lastFishingStart = null;
            vanillaPose = null;
        }
        boolean focus = client.isWindowActive();
        if (focus != focused) {
            INPUT.resetHeld();
            if (focus || !config().unfocused) stop();
            focused = focus;
        }
        observedMotion = player == null || lastPosition == null ? Vec3.ZERO : player.position().subtract(lastPosition);
        if (player != null && boat(player.getVehicle()) && player.getVehicle() == lastVehicle)
            observedMotion = lastVehicle.position().subtract(lastVehiclePosition);
        boolean moved = player != null && lastPosition != null && movedFrom(lastPosition, false);
        // Controller look can change player rotation without a keyboard or mouse callback.
        boolean turned = player != null && (Math.abs(Math.IEEEremainder(player.getYRot() - lastYaw - carriedYaw, 360)) > 0.001
            || Math.abs(player.getXRot() - lastPitch) > 0.001);
        carriedYaw = 0;
        boolean jumped = player != null && lastPosition != null && player.position().distanceToSqr(lastPosition) > 256;
        if (player != null) {
            lastPosition = player.position();
            lastYaw = player.getYRot();
            lastPitch = player.getXRot();
            lastVehicle = player.getVehicle();
            lastVehiclePosition = lastVehicle == null ? null : lastVehicle.position();
        }
        boolean eligible = eligible() && (!moved || travelling()) && (!turned || aiming()) && !jumped;
        if (session != null) {
            if (!eligible || !travelling() && movedFrom(anchor, true)) stop();
            else {
                if (movingGeometry != null) session.bearing(travelBearing());
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
            if (eligible && vanillaPose != null) { start(true); manualWait = 0; }
            else if (manualWait == 0) message("unavailable");
        } else if (activityKind() == Scene.Kind.FISHING) {
            // A cast starts once its bobber reaches water; waking never restarts the same cast.
            if (eligible && config().automatic && FishingActivity.hook(player, world) != lastFishingStart) start(false);
            IDLE.reset(now);
        } else if (IDLE.ready(now, config().idleSeconds, eligible && config().automatic)) start(false);
        CinematicMusic.tick();
    }

    private static boolean eligible() {
        Minecraft client = Minecraft.getInstance();
        if (store == null || !config().enabled || !conflict.isEmpty() || player == null || world == null
            || client.player != player || client.level != world || client.getConnection() != connection
            || screen() != null || client.isPaused()
            || (!client.isWindowActive() && !config().unfocused)
            || !steering() && (activityUse == null ? INPUT.held() : INPUT.heldExcept(token(activityUse)))) return false;
        //? if >=26.2 {
        /*if (client.gui.overlay() != null) return false;
        *///?} else {
        if (client.getOverlay() != null) return false;
        //?}
        var options = client.options;
        if (!steering() && movementInput()) return false;
        if ((!steering() && (options.keyUp.isDown() || options.keyDown.isDown() || options.keyLeft.isDown() || options.keyRight.isDown()
            || options.keyJump.isDown())) || options.keyAttack.isDown() || options.keyUse.isDown() && !allowRodUse() || options.keyDrop.isDown()) return false;
        Scene.Kind kind = activityKind();
        if (session != null && (kind != activity || player.getVehicle() != vehicle)) return false;
        if (session != null && activity == Scene.Kind.FISHING && FishingActivity.hook(player, world) != fishingHook) return false;
        return player.isAlive() && !player.isSleeping() && (!player.isPassenger() || kind == Scene.Kind.BOAT || kind == Scene.Kind.FISHING && boat(player.getVehicle()))
            && (!player.isFallFlying() || kind == Scene.Kind.FLIGHT) && !player.isInLava() && !player.isUsingItem()
            && !player.isOnFire() && player.hurtTime == 0 && !player.isSpectator();
    }

    private static boolean movedFrom(Vec3 origin, boolean fromAnchor) {
        // Water buoyancy is not user input. Keep a bounded allowance for passive bobbing.
        double limit = player.isInWater() ? (fromAnchor ? 2 : 0.12) : 0.01;
        return player.position().distanceToSqr(origin) > limit * limit;
    }

    private static void start(boolean manual) {
        if (!eligible() || vanillaPose == null) return;
        anchor = player.position();
        Vec subject = WorldGeometry.point(anchor.add(0, player.getBbHeight() * 0.65, 0));
        activity = activityKind();
        manualTravel = manual && (activity == Scene.Kind.BOAT || activity == Scene.Kind.FLIGHT);
        fishingHook = activity == Scene.Kind.FISHING ? FishingActivity.hook(player, world) : null;
        if (fishingHook != null) lastFishingStart = fishingHook;
        vehicle = player.getVehicle();
        course = vehicle == null ? player.getYRot() : vehicle.getYRot();
        if (activity == Scene.Kind.PLAYER) {
            session = new CinematicSession(subject, vanillaPose, player.getYRot(), config(), new WorldGeometry(world), RANDOM);
        } else {
            movingGeometry = new MovingGeometry(new WorldGeometry(world).forScene(null), subject, System.nanoTime());
            Vec bobber = fishingHook == null ? null : FishingActivity.target(fishingHook, 1, subject);
            session = new CinematicSession(new Vec(0, 0, 0), movingGeometry.local(vanillaPose), travelBearing(), config(), movingGeometry, RANDOM, activity, bobber);
        }
        session.tick(System.nanoTime());
        manualFeedback = manual && !session.showing();
    }

    public static void requestStart() {
        stop();
        if (store != null) manualWait = 20;
    }

    public static void stop() {
        if (activityUse != null) {
            INPUT.suppress(token(activityUse));
            clearBindings(activityUse);
            activityUse = null;
        }
        session = null;
        movingGeometry = null;
        activity = Scene.Kind.PLAYER;
        manualTravel = false;
        vehicle = null;
        fishingHook = null;
        appliedCamera = null;
        audioReady = false;
        anchor = null;
        manualWait = 0;
        manualFeedback = false;
        IDLE.reset(System.nanoTime());
    }

    public static void nextShot() { if (session != null) session.nextShot(); }

    public static Runnable holdForScreenshot() {
        CinematicSession owner = session;
        if (owner == null || !owner.showing()) return () -> { };
        owner.beginCapture(System.nanoTime());
        var released = new AtomicBoolean();
        return () -> {
            if (released.compareAndSet(false, true)) Minecraft.getInstance().execute(() -> {
                if (session == owner) owner.endCapture(System.nanoTime());
            });
        };
    }

    public static boolean fabrishotKey(InputConstants.Key key) {
        for (KeyMapping binding : Minecraft.getInstance().options.keyMappings)
            if (binding.getName().equals("key.fabrishot.screenshot") && matches(binding, key)) return true;
        return false;
    }

    public static Pose cameraFrame(Camera camera, float delta) {
        appliedCamera = null;
        if (session == null) {
            //? if >1.21.10 {
            vanillaPose = new Pose(WorldGeometry.point(camera.position()), camera.yRot(), camera.xRot());
            //?} else {
            /*vanillaPose = new Pose(WorldGeometry.point(camera.getPosition()), camera.getYRot(), camera.getXRot());
            *///?}
            return null;
        }
        if (!eligible() || !travelling() && movedFrom(anchor, true)) { stop(); return null; }
        long now = System.nanoTime();
        Vec bobber = null;
        if (movingGeometry != null) {
            float partial = world.tickRateManager().isEntityFrozen(player) ? 1 : delta;
            Vec subject = new Vec(player.xOld + (player.getX() - player.xOld) * partial,
                player.yOld + (player.getY() - player.yOld) * partial + player.getBbHeight() * 0.65,
                player.zOld + (player.getZ() - player.zOld) * partial);
            if (!movingGeometry.follow(subject, now, session.capturing())) { stop(); return null; }
            if (fishingHook != null) {
                bobber = FishingActivity.target(fishingHook, partial, subject);
                session.fishing(bobber);
            }
        }
        Pose pose = session.frame(now);
        if (pose != null && movingGeometry != null) {
            pose = movingGeometry.apply(pose, session.focus(), session.fade(), session.take(), session.capturing());
            if (pose != null && bobber != null && !movingGeometry.sees(pose, bobber)) pose = null;
            if (pose == null) { stop(); return null; }
        }
        if (session.ended()) stop();
        if (pose != null && session != null) appliedCamera = camera;
        return pose;
    }

    public static boolean input(InputConstants.Key key, int action, boolean screenshot) {
        if (store == null) return false;
        long token = token(key);
        if (action == InputConstants.RELEASE) {
            IDLE.reset(System.nanoTime());
            if (key.equals(activityUse)) {
                if (!Minecraft.getInstance().isWindowActive()) { stop(); return true; }
                activityUse = null;
            }
            // Focus loss can synthesize releases while the physical key is still held.
            if (!Minecraft.getInstance().isWindowActive() && INPUT.suppressed(token)) return true;
            if (INPUT.release(token)) {
                clearBindings(key);
            }
            return false;
        }
        var options = Minecraft.getInstance().options;
        boolean dangerous = matches(options.keyAttack, key) || matches(options.keyUse, key) || matches(options.keyDrop, key);
        if (screenshot && action == InputConstants.PRESS) {
            // Capture handlers read the event directly; shared bindings and repeats stay suppressed.
            if (ownsCamera()) INPUT.suppress(token);
            return false;
        }
        if (screenshot) return INPUT.suppressed(token);
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
        boolean rod = matches(options.keyUse, key) && !matches(options.keyAttack, key) && !matches(options.keyDrop, key)
            && !movementBinding(key) && allowRodUse();
        boolean suppressed = INPUT.press(token, ownsCamera() && dangerous && !rod);
        if (rod && !suppressed) {
            activityUse = key;
            IDLE.reset(System.nanoTime());
            return false;
        }
        if (steering() && !dangerous && movementBinding(key)) {
            IDLE.reset(System.nanoTime());
            return suppressed;
        }
        stop();
        return suppressed;
    }

    public static boolean matches(KeyMapping mapping, InputConstants.Key key) {
        return ((KeyMappingAccess) mapping).afkCinematics$key().equals(key);
    }

    private static boolean movementBinding(InputConstants.Key key) {
        var options = Minecraft.getInstance().options;
        return matches(options.keyUp, key) || matches(options.keyDown, key) || matches(options.keyLeft, key)
            || matches(options.keyRight, key) || matches(options.keyJump, key) || matches(options.keyShift, key) || matches(options.keySprint, key);
    }

    public static boolean suppressed(InputConstants.Key key) { return INPUT != null && INPUT.suppressed(token(key)); }
    public static boolean retainsInput(InputConstants.Key key) { return suppressed(key) || key.equals(activityUse); }

    public static boolean allowRodUse() {
        var client = Minecraft.getInstance();
        return session != null && activity == Scene.Kind.FISHING && player == client.player && world == client.level
            && connection == client.getConnection() && FishingActivity.hook(player, world) == fishingHook
            && FishingActivity.canUse(player, client.hitResult);
    }

    private static void clearBindings(InputConstants.Key key) {
        KeyMapping.set(key, false);
        for (KeyMapping binding : Minecraft.getInstance().options.keyMappings) {
            if (matches(binding, key)) {
                binding.setDown(false);
                ((KeyMappingAccess)binding).afkCinematics$clicks(0);
            }
        }
    }

    public static void blockInput(InputConstants.Key key) {
        INPUT.press(token(key), true);
        clearBindings(key);
        stop();
    }

    private static long token(InputConstants.Key key) {
        return ((long)key.getType().ordinal() << 32) | (key.getValue() & 0xffffffffL);
    }

    public static void mouseMoved(double x, double y) {
        if (Minecraft.getInstance().isWindowActive() && Math.abs(x) + Math.abs(y) > 0.01) {
            IDLE.reset(System.nanoTime());
            if (!aiming()) stop();
        }
    }

    private static boolean movementInput() {
        var movement = player.input.getMoveVector();
        // Controller integrations need not produce keyboard callbacks.
        if (movement.x * movement.x + movement.y * movement.y > 0.0001) return true;
        //? if >=1.21.2 {
        return player.input.keyPresses.jump() || player.input.keyPresses.shift();
        //?} else {
        /*return player.input.jumping || player.input.shiftKeyDown;
        *///?}
    }

    private static boolean boat(Entity entity) {
        //? if >=1.21.2 {
        return entity instanceof AbstractBoat;
        //?} else {
        /*return entity instanceof Boat;
        *///?}
    }

    private static Scene.Kind activityKind() {
        if (player == null || store == null) return Scene.Kind.PLAYER;
        if (config().fishing && !player.isFallFlying() && !player.getAbilities().flying
            && FishingActivity.hook(player, world) != null) return Scene.Kind.FISHING;
        if (config().boats && boat(player.getVehicle())) return Scene.Kind.BOAT;
        if (config().flight && !player.isPassenger() && (player.isFallFlying() || player.getAbilities().flying)) return Scene.Kind.FLIGHT;
        return Scene.Kind.PLAYER;
    }

    private static boolean steering() {
        return store != null && config().travelControls && (movingGeometry != null && manualTravel
            || session == null && manualWait > 0 && (activityKind() == Scene.Kind.BOAT || activityKind() == Scene.Kind.FLIGHT));
    }

    private static boolean aiming() { return steering() || session != null && activity == Scene.Kind.FISHING; }

    private static boolean travelling() {
        Scene.Kind kind = activityKind();
        return kind == Scene.Kind.BOAT || kind == Scene.Kind.FLIGHT || kind == Scene.Kind.FISHING && boat(player.getVehicle());
    }

    private static double travelBearing() {
        // Remote boats move through interpolation, with no useful velocity on the passenger client.
        if (observedMotion.horizontalDistanceSqr() > 0.0025) course = Math.toDegrees(Math.atan2(-observedMotion.x, observedMotion.z));
        return course;
    }

    public static void passengerTurn(Entity passenger, double degrees) {
        if (passenger == player) carriedYaw += Math.IEEEremainder(degrees, 360);
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
