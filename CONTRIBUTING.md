# Contributing

The canonical repository is [ohne-b/afk-cinematics](https://github.com/ohne-b/afk-cinematics).
The project uses the [GPL-3.0-only license](LICENSE.md). Fabric and NeoForge are supported;
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
On 1.21.11, `-PcompatTest` adds pinned Sodium, Iris, Dynamic FPS, Fabrishot and Mod Menu versions.
On 1.21.8 it adds Fabrishot for the legacy capture check.
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

Start branches from current canonical `main`. Fetch and incorporate it before final
validation and independent review, and again before merging if it has advanced.
Resolve conflicts and rerun affected checks; do not repeat unrelated successful CI.
Use the PR template to record the base, tested revision, evidence and review outcome.
Ordinary changes go through PRs rather than direct pushes to `main`.

Verify the Git author and committer before committing. Maintainer contributions use
the verified public `ohne-b` noreply identity, never a private name or email address.
Keep credentials out of source, artifacts, logs and screenshots.

Implementation does not authorize merging, releasing or deploying. When merging is
authorized, use a merge commit with a conventional subject and PR number, for example
`feat(camera): add idle cinematics (#1)`. Do not rewrite published history or tags.

Releases require explicit authorization. Publish only validated artifacts with exact
Minecraft and loader requirements. Use consistent artifact names and concise release
notes with relevant PR numbers. A normal commit or merge must not publish a release.

### Preparing a release

Maintain concise `CHANGELOG.md` entries under `Unreleased`, with relevant PR numbers.
Run **Prepare release** from `main` with the next version. It updates `mod_version`
and moves those notes into a version entry in a draft PR. Review the notes before
merging. Stable notes must cover everything since the previous stable release,
including changes already described in release candidates.

The workflow uses a dedicated `PUBLISHER_TOKEN` secret with repository contents and
pull-request write access so its PR triggers normal CI. Never copy a local CLI token
into this secret. Without it, run `python .github/scripts/release.py prepare VERSION`
on a `feat/release-VERSION` branch and open the draft PR normally. Python 3.11+ is
required; test the helper with `python -m unittest discover -s .github/scripts -v`.

### Publishing and distribution

After explicit release authorization, run **Publish release** from `main` with the
reviewed version, full merged commit SHA and its successful **Build** push run ID.
The default is validation only: it verifies all target jars and prepares a downloadable
bundle without creating tags, releases or platform versions. It reuses CI artifacts;
if they expired, rerun Build for that same revision rather than substituting local jars.

Select `github` and enable `publish` to upload the jars, SHA-256 checksums and complete
source archive. Files are downloaded and verified before the draft becomes public.
Stable releases become latest; alpha/beta/RC versions remain prereleases. An existing
release stops publication instead of overwriting assets or tags.

Then run the same inputs with destination `modrinth` or `curseforge`. Distribution
requires the matching public GitHub release and checksum manifest. Each Minecraft
version/loader is a separate upload, with Fabric API required for Fabric builds.
The pinned [mc-publish action](https://github.com/Kira-NT/mc-publish) handles these
uploads. Configure the destinations you use in GitHub's `release` environment:

| Destination | Variable | Secret |
| --- | --- | --- |
| Modrinth | `MODRINTH_PROJECT_ID` | `MODRINTH_TOKEN` |
| CurseForge | `CURSEFORGE_PROJECT_ID` (numeric) | `CURSEFORGE_TOKEN` |

Create the platform projects separately, with GPL-3.0-only licensing and client-only
support. Tokens belong in GitHub secrets, never chat or source. Environment protection
can restrict publishing to maintainers. CurseForge is optional; it does not block
GitHub or Modrinth publication.

Platform uploads are not transactional. If a run partly succeeds, inspect the uploaded
files before retrying only failed jobs; rerunning successful jobs may create duplicates.
If upload acknowledgement was lost, verify the destination before retrying. A failed
GitHub publication may leave a draft: inspect and finish it manually without replacing
existing files. Never delete or rewrite a published release to recover a failed run.
