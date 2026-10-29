# Repository instructions

Read [CONTRIBUTING.md](CONTRIBUTING.md) before editing or reviewing. It owns the
development workflow, validation, review requirements and release boundaries.

Keep idle timing, shot selection and camera math in `core/`. Minecraft world queries,
rendering, input and loader integration belong in `src/main/`. Only one controller
owns the cinematic. Bind its lifetime to the current player, world and connection.

Change the rendered camera only. Never move or rotate the player, send synthetic
gameplay input, alter inventory, keep a server session active, or change persistent
game options to produce a cinematic. Restore control before processing wake input.
An attack, use or drop action that wakes the camera must stay suppressed until release.
Keep suppression across world/player replacement and vanilla key-state restoration.
Track the effective mouse button after vanilla's platform-specific remapping.
Inactive release events can be synthetic; they do not establish physical release.

Validate the swept camera volume and subject visibility using loaded terrain only.
Apply the pose before culling and render extraction. Keep world access on the client
thread; rendering consumes an immutable pose. Stop safely on invalid state or an
obstructed path. Every stop path uses the same cleanup.
Separate scenery viewpoints use covered cuts: render a fully opaque old frame,
then a fully opaque destination frame, and revalidate before revealing it. Keep all
viewpoints within the player-centered range; never interpolate through terrain.

Use isolated worlds for tests. Never access player saves or public servers in automated
tests. A build does not establish visual quality or compatibility. Use a separate
read-only reviewer for independent review and provide the contributor rules and
actual validation evidence. Keep this file short; do not add planning documents.
