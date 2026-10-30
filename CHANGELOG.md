# Changelog

## Unreleased

- Simplify settings navigation and give each activity its own start choice. (#43)
- Hide HUD follows F1 visibility without changing the player's saved HUD state (#42).
- Minecart travel views, normal FPS caps during activities and a configurable K start/stop key (#41).
- Four new scenery drives, less repetitive sequences, adjustable player-shot frequency and new shots enabled on update (#31).
- Controller-look wake detection and smooth resumption after client hitches (#32).
- Boat, raft and flight tracking views with optional steering during manually started cinematics (#33).
- Fishing views follow the angler, line and bobber, with prompt entry and normal reeling controls (#34).
- Low, high and broadside travel views follow gradual turns and cover sharp course changes (#35).
- Manually started Elytra views keep filming during normal rocket boosts (#36).
- Moving takes continue through Next-shot preparation and fades (#37).
- The shot picker separates scenery, travel and fishing selections, with presets scoped to each set (#38).
- Compatible Minecraft versions share release jars, reducing downloads from 34 files to 19 while keeping every supported version (#39).

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
