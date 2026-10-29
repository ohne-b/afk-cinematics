<p align="center">
  <img src="assets/logo.svg" width="180" alt="AFK Cinematics logo">
</p>

<h1 align="center">AFK Cinematics</h1>

<p align="center">
  <a href="LICENSE.md"><img src="https://img.shields.io/badge/license-GPL--3.0--only-green?style=flat-square" alt="GPL-3.0-only license"></a>
</p>

Calm camera shots while you take a break from Minecraft. Move to return to your
normal view. Client-side, for Fabric and NeoForge.

This is an independent implementation by [ohne-b](https://github.com/ohne-b).
Development targets Minecraft 1.21 through 26.3. No release has been published.

## Install and use

Install the jar matching your Minecraft version and loader. Fabric also needs
Fabric API. Nothing needs to be installed on the server.

Stand still for 30 seconds to start a cinematic. Movement or opening a menu restores
your normal view. An attack, use or drop press that wakes the camera is consumed;
release it before interacting. F2 and Fabrishot capture the cinematic view without
ending it.

Open settings with `/afkcinematics settings`, Mod Menu on Fabric, or the Mods screen
on NeoForge. `/afkcinematics start` previews a cinematic; `/afkcinematics stop` ends it.
Optional Toggle, Next shot and Settings keys are under Controls.

Mixed mode favors scenery with occasional player shots, with nearby scenery as a fallback in
tight spaces. Choose the subject, shot types and pacing in settings. Separate
viewpoints use gentle fades; the camera does not travel through walls.

## Compatibility

The camera stays near the player and uses loaded terrain. It does not move your
character, change server activity or prevent AFK kicks. Display settings include a
separate cinematic FPS cap; normal FPS settings resume on exit. Paused and minimized
windows keep their normal behaviour. VSync and hardware still limit actual FPS.

Cinematics require a stationary player with no menu open. If no clear view is
available, the normal camera stays active. Freecam is allowed, including while active.
Other known camera replacements are identified in settings when they prevent
activation. Shader and performance-mod
compatibility needs testing with the exact versions used.

AmbientSounds keeps playing. Its water depth follows the cinematic camera with
6.3.5 on Fabric 1.21.11 and 6.3.11 on 26.3; other versions and its biome/weather
selection keep their own behaviour.

Settings are stored in `config/ohneb-afk-cinematics.json`. An unreadable file is
preserved and cinematics are disabled until it is corrected.

## Development

See [CONTRIBUTING.md](CONTRIBUTING.md) for setup, validation and review requirements.

## License

[GPL-3.0-only](LICENSE.md)
