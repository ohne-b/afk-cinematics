# Changelog

## Unreleased

- Sleep cinematics frame your bed, then nearby scenery during longer waits. Vanilla chat, Leave Bed and sleep timing stay intact; automatic sleep views can be disabled in settings.

## 0.1.0-alpha.1

Press **F6** to film while you walk, swim, ride, fly or fish. Press it again to return to your normal view. The key is configurable; automatic AFK views still use the idle timer.

- Travel cameras for boats, rafts, minecarts, mounts and flight, with normal controls and Elytra rocket use. Front-facing travel shots can be switched off. (#33, #35, #36, #41, #52–54)
- Smoother following over jumps, hills and sharp turns. Cameras adjust around trees, walls and low cover, and swimming views stay steady through surface hops. (#47, #50–52, #54–56, #60, #62, #63)
- Roadside and Overlook shots plant the camera beside your route and pan as you pass. (#60, #62)
- Fishing views frame the angler and bobber, start once per cast and keep music playing briefly between casts. (#34, #50, #53)
- More scenery drives, less repetition and occasional wildlife views during automatic AFK cinematics. New shot types are enabled when added. (#31, #53)
- Choose instant cuts or short fades, including a 6–10 second shot length without speeding up camera movement. Black bars ease in on entry and disappear immediately on exit. (#37, #57–60)
- Use your game FOV or choose a cinematic FOV from 30–140°. (#58)
- Cleaner settings with a compact sidebar, grouped shot choices, clearer sliders and keyboard navigation. (#38, #43, #46, #48, #53–55, #61)
- Choose game music, creative music, music discs or a custom sound event, adjust volume and control music from chat. Chat can stay open during cinematics. (#44, #45)
- Hide HUD exposes F1 visibility to other HUD mods during rendering without changing your saved F1 state. The cinematic FPS cap applies only to automatic AFK views. (#41, #42, #49)
- Fix automatic AFK failing to restart after focus changes; improve controller-look detection and recovery after client hitches. (#32, #64)
- Compatible Minecraft versions now share jars: 19 downloads cover the same 17 versions on Fabric and NeoForge. (#39)

Install only the jar listing your Minecraft version and loader. Fabric requires Fabric API. No server installation is needed.

Still an early alpha. Camera views need nearby loaded terrain; shader packs and individual HUD or camera mods may behave differently.

Changelog: https://github.com/ohne-b/afk-cinematics/compare/v0.0.1-alpha.1...v0.1.0-alpha.1

Please [open an issue](https://github.com/ohne-b/afk-cinematics/issues/new) to report bugs or request features.

## 0.0.1-alpha.1

First public alpha for Fabric and NeoForge, with separate jars for Minecraft 1.21 through 26.3.

- Client-side AFK cinematics for Fabric and NeoForge, with protected wake inputs (#1).
- Selectable camera drives, wide aerial passes, low scenery views, underwater shots and angled player framing (#3, #4, #15, #16, #19, #26).
- Cinematic lenses, a separate FPS cap and clean HUD, hand and name-tag rendering (#12, #14, #18, #20).
- F2 and supported Fabrishot captures preserve the cinematic view without interrupting it (#21).
- Stationary flying and underwater entry, camera-relative vanilla underwater ambience, and an optional AmbientSounds water-depth adapter (#24, #25, #27).
- Camera water transitions change the ambience without playing entry or exit splashes; normal player water sounds are preserved (#28).

Install only the jar matching your Minecraft version and loader. Fabric requires Fabric API. No server installation is needed.

This is an early alpha. Shader and other camera-mod combinations need testing with the exact versions used. The AmbientSounds adapter supports 6.3.5 on Fabric 1.21.11 and 6.3.11 on Fabric/NeoForge 26.3; other versions keep their own sampling.
