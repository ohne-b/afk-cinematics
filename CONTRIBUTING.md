# Contributing

The canonical repository is [ohne-b/afk-cinematics](https://github.com/ohne-b/afk-cinematics).
The project uses the [MIT license](LICENSE.md). Fabric and NeoForge are supported;
Forge is outside the project scope.

## Development

Run Gradle with JDK 25. Minecraft 1.21.x uses a JDK 21 toolchain; 26.x uses JDK 25.
Set `JAVA_HOME` to JDK 25 and, when needed, `JDK21` and `JDK25` to the installed JDK
directories. Use the committed wrapper:

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :1.21.11-fabric:build
.\gradlew.bat :26.3-neoforge:build
.\gradlew.bat :1.21.11-fabric:runClient
```

Shared source is processed by Stonecutter. Keep version conditions at API boundaries
and behavior in the shared core. Prefer concrete classes and existing game APIs.
Use vanilla controls and the message catalog for UI text. Do not add dependencies,
forwarding layers or extension systems without a concrete need.

Preserve unrelated changes, settings, credentials, saves, logs and backups. Reuse
installed toolchains and build output. Do not delete caches or generated output
without authorization. Put screenshots and validation logs in ignored `artifacts/`.

## Validation and review

Inspect the working tree before editing. Test meaningful behavior and realistic
failure paths, including interruption, obstructed camera paths, world replacement,
held inputs and settings failures. Use disposable worlds and local test servers.
Never use a normal player world or public server as an automated test.

Build each affected Minecraft/loader target. Check camera entry, transitions and exit
in a running client, including low frame rates and narrow screens. Record exact
commands, tested revisions, results and limitations. Compilation is not gameplay
evidence. Do not count pending checks as passes or repeat valid checks unnecessarily.
Run `git diff --check` and check metadata, commands and documentation links.

Client game tests run with `:1.21.11-fabric:runClientGameTest`,
`:26.2-fabric:runClientGameTest` or `:26.3-fabric:runClientGameTest`.
They create isolated worlds under `build/run/`.
`:1.21.8-fabric:runClientGameTest` covers the legacy touchscreen input path.
On 1.21.11, `-PcompatTest` adds pinned Sodium, Iris, Dynamic FPS and Mod Menu versions.
Shader packs are a separate local check; CI runs that profile without a shader pack.
`-PsmokeTest` makes `runClient` check startup and settings, then exit. Add
`-PsceneWorld=create` to create a flat test world and check player shots, a cramped
scenery fallback, the rendered camera and unchanged server state. CI runs this on
every target. `-PsceneWorld=<name>` may reuse a copied test fixture, never a player save.
The Fabric suite also checks natural terrain and records camera timings in `artifacts/`.
Use `-PnaturalSeconds=3600` for an hour of continuous scenery validation; the default is 40 seconds.
Use `-PcinematicCapture=<new-name>` for timestamped frames from a fixed snowy-forest
view. Capture labels must be new so earlier footage is preserved.
Add `-PcinematicScene=plains` or `-PcinematicScene=cavern` for the other view fixtures.

Every PR requires independent critical review by a human or a separate read-only
reviewer who did not write it. Provide these instructions, acceptance criteria, the
revision and actual validation evidence. Fix blocking findings and have substantive
changes rechecked. The author's reread is not independent review.

## Git and releases

Use descriptive `feat/` or `fix/` branches, focused conventional commits and
conventional PR titles. Split substantial work into meaningful changes. Keep PR
descriptions about the resulting behavior and validation, without conversational
history. Incomplete work stays in a draft PR.

Verify the Git author and committer before committing. Maintainer contributions use
the verified public `ohne-b` noreply identity, never a private name or email address.
Keep credentials out of source, artifacts, logs and screenshots.

Implementation does not authorize merging, releasing or deploying. When merging is
authorized, use a merge commit with a conventional subject and PR number, for example
`feat(camera): add idle cinematics (#1)`. Do not rewrite published history or tags.

Releases require explicit authorization. Publish only validated artifacts with exact
Minecraft and loader requirements. Use consistent artifact names and concise release
notes with relevant PR numbers. A normal commit or merge must not publish a release.
