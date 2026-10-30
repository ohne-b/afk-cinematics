# Repository instructions

Read [CONTRIBUTING.md](CONTRIBUTING.md) before editing or reviewing. It owns the
development workflow, validation, review requirements and release boundaries.

Keep idle timing, shot selection and camera math in `core/`. Minecraft world queries,
rendering, input and loader integration belong in `src/main/`. Only one controller
owns the cinematic. Bind its lifetime to the current player, world and connection.

Change the rendered camera only; preserve the selected camera entity, including
another mod's camera. Never move or rotate the player, send synthetic
gameplay input, alter inventory, keep a server session active, or change persistent
game options to produce a cinematic. Restore control before processing wake input.
An attack, use or drop action that wakes the camera must stay suppressed until release.
Keep suppression across world/player replacement and vanilla key-state restoration.
Consumed cinematic controls retain suppression through screens until physical release.
Chat may retain an existing session; screen-consumed keys cannot become gameplay on close.
Only explicit manual look permission may relax ordinary view rotation wake.
Activity start choices are independent of AFK auto-start.
Only manually started travel views may retain steering. Follow the rendered subject
without changing its pose; validate movement from the last applied world-camera position.
Fishing views follow only the current player's live bobber. Permit genuine rod use
only through vanilla's hand routing; never cast, retrieve or infer a catch automatically.
Manual Elytra views may pass genuine rocket use through the same hand and input guards.
Track the effective mouse button after vanilla's platform-specific remapping.
Inactive release events can be synthetic; they do not establish physical release.

Validate the swept camera volume and subject visibility using loaded terrain only.
Check the client chunk cache directly; `ClientLevel.hasChunk` does not prove loading.
Planning must bound the camera curve and moving sightline between samples.
Apply the pose before culling and render extraction. Keep world access on the client
thread; rendering consumes an immutable pose. Stop safely on invalid state or an
obstructed path. Every stop path uses the same cleanup.
Apply the transient lens only to the world projection paired with that pose, before
culling. Preserve the hand projection, panorama mode and the user's FOV option.
Override the frame cap only for ordinary AFK views, not fishing or travel; preserve normal options and
minimized-window throttling. Do not simulate activity to avoid idle limits.
Separate scenery viewpoints use covered cuts: render a fully opaque old frame,
then a fully opaque destination frame, and revalidate before revealing it. Keep all
viewpoints within the player-centered range; never interpolate through terrain.
Underwater takes require a fully submerged camera volume. Use each view's environment
for planning and covered handoffs; water may be passable, lava and solid shapes are not.
Prepare the next take without freezing the current shot. Revalidate at handoff and
cover changes of viewpoint or direction. Manual Next discards the prepared plan.
Screenshot holds pause the take's timing, not terrain validation. Release each hold
only for its owning session; a finished capture must not resume a newer session.
Underwater audio follows the last applied camera, not a pending scene. Keep the
player's real fluid state unchanged and reconcile its normal ambience after stopping.

Use isolated worlds for tests. Never access player saves or public servers in automated
tests. A build does not establish visual quality or compatibility. Use a separate
read-only reviewer for independent review and provide the contributor rules and
actual validation evidence. Keep this file short; do not add planning documents.

Keep the README short and easy to scan. Do not add a paragraph for every feature or
fix; change it only when essential setup, usage or project information changes.
Put contributor rules here or in CONTRIBUTING.md, and release details in CHANGELOG.md.
