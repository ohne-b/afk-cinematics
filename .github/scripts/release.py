"""Prepare and verify releases; publish-github is an explicitly invoked write operation."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tomllib
from zipfile import ZipFile


REPOSITORY = "ohne-b/afk-cinematics"
MOD_ID = "ohneb_afk_cinematics"
VERSION = re.compile(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-(alpha|beta|rc)\.(0|[1-9]\d*))?")
# Shared jars must also pass the complete payload comparison below.
COMPATIBLE_GAMES = (
    ("1.21", "1.21.1"), ("1.21.2", "1.21.3", "1.21.4"),
    ("1.21.6", "1.21.7", "1.21.8"), ("1.21.9", "1.21.10"),
    ("26.1", "26.1.1", "26.1.2"),
)


def version_key(version):
    match = VERSION.fullmatch(version)
    if not match:
        raise ValueError("Use X.Y.Z or X.Y.Z-alpha.N, -beta.N or -rc.N")
    major, minor, patch, stage, number = match.groups()
    return (int(major), int(minor), int(patch),
            {"alpha": 0, "beta": 1, "rc": 2, None: 3}[stage], int(number or 0))


def properties(path):
    return dict(line.split("=", 1) for line in path.read_text().splitlines()
                if "=" in line and not line.startswith("#"))


def current_version(root):
    return properties(root / "gradle.properties")["mod_version"]


def section(changelog, heading):
    match = re.search(r"^## " + re.escape(heading) + r"\n(.*?)(?=^## |\Z)",
                      changelog, re.M | re.S)
    if not match or not match[1].strip() or "TODO" in match[1]:
        raise ValueError(f"Add reviewed release notes under '## {heading}' in CHANGELOG.md")
    return match[1].strip()


def prepare(root, version):
    if version_key(version) < version_key(current_version(root)):
        raise ValueError("The release version cannot go backwards")
    changelog = root / "CHANGELOG.md"
    text = changelog.read_text(encoding="utf-8")
    section(text, "Unreleased")
    if re.search(r"^## " + re.escape(version) + r"$", text, re.M):
        raise ValueError("This version already has a changelog entry")
    text = text.replace("## Unreleased\n", f"## Unreleased\n\n## {version}\n", 1)
    config = root / "gradle.properties"
    config.write_text(re.sub(r"^mod_version=.*$", f"mod_version={version}",
                            config.read_text(), flags=re.M), encoding="utf-8")
    changelog.write_text(text, encoding="utf-8")


def targets(root, version):
    result = []
    for config in sorted((root / "versions").glob("*/gradle.properties")):
        target = config.parent.name
        match = re.fullmatch(r"([0-9]+(?:\.[0-9]+){1,2})-(fabric|neoforge)", target)
        if not match:
            raise ValueError(f"Unsupported target: {target}")
        minecraft, loader = match.groups()
        config_values = properties(config)
        if config_values["minecraft_version"] != minecraft:
            raise ValueError(f"Mismatched target properties: {target}")
        result.append({"target": target, "minecraft": minecraft, "game_versions": minecraft, "loader": loader,
                       "java": 21 if minecraft.startswith("1.21") else 25,
                       "file": f"afk-cinematics-{version}+mc{target}.jar",
                       "dependencies": "fabric-api(required){modrinth:P7dR8mSH}{curseforge:306612}"
                       if loader == "fabric" else ""})
        if loader == "neoforge":
            result[-1]["neoforge_range"] = f"[{config_values['neoforge_version']},)"
    if not result:
        raise ValueError("No Minecraft targets found")
    return result


def validate_run(run, sha):
    if not (run.get("repository", {}).get("full_name") == REPOSITORY
            and run.get("head_repository", {}).get("full_name") == REPOSITORY
            and run.get("path") == ".github/workflows/build.yml"
            and run.get("event") == "workflow_dispatch" and run.get("head_branch") == "main"
            and run.get("head_sha") == sha and run.get("status") == "completed"
            and run.get("conclusion") == "success"):
        raise ValueError("Use a successful manual Build run for this exact main commit")


def validate_jar(path, target, version, license_text):
    with ZipFile(path) as jar:
        if jar.testzip() is not None:
            raise ValueError(f"Corrupt jar: {path.name}")
        names = jar.namelist()
        if any("gametest" in name.lower() or "/smoke/" in name.lower() for name in names):
            raise ValueError(f"Test fixtures in {path.name}")
        if jar.read("LICENSE.md").decode().replace("\r\n", "\n") != license_text:
            raise ValueError(f"License mismatch in {path.name}")
        expected = f"{version}+mc{target['target']}"
        games = target.get("game_versions", target["minecraft"]).splitlines()
        if target["loader"] == "fabric":
            meta = json.loads(jar.read("fabric.mod.json"))
            valid = (meta["id"] == MOD_ID and meta["version"] == expected
                     and meta["environment"] == "client" and meta["license"] == "GPL-3.0-only"
                     and meta["depends"]["minecraft"] == (games if len(games) > 1 else games[0])
                     and "META-INF/neoforge.mods.toml" not in names)
        else:
            meta = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode())
            minecraft = next(d for d in meta["dependencies"][MOD_ID] if d["modId"] == "minecraft")
            neoforge = next(d for d in meta["dependencies"][MOD_ID] if d["modId"] == "neoforge")
            valid = (meta["mods"][0]["modId"] == MOD_ID and meta["mods"][0]["version"] == expected
                     and meta["license"] == "GPL-3.0-only"
                     and minecraft["versionRange"] == ",".join(f"[{game}]" for game in games)
                     and neoforge["versionRange"] == target["neoforge_range"]
                     and minecraft["side"] == "CLIENT" and "fabric.mod.json" not in names)
        if not valid:
            raise ValueError(f"Wrong version, loader or license in {path.name}")


def comparable_payload(entries, target):
    """Ignore only fields intentionally replaced by grouped release metadata."""
    payload = dict(entries)
    if target["loader"] == "fabric":
        meta = json.loads(payload.pop("fabric.mod.json"))
        meta["version"] = "<release>"
        meta["depends"]["minecraft"] = "<games>"
        # Loom records the compile target here; retain all its other attributes.
        if "META-INF/MANIFEST.MF" in payload:
            payload["META-INF/MANIFEST.MF"] = re.sub(
                rb"(?m)^(Fabric-Minecraft-Version: )" + re.escape(target["minecraft"].encode()) + rb"(\r?)$",
                rb"\1<target>\2", payload["META-INF/MANIFEST.MF"])
    else:
        meta = tomllib.loads(payload.pop("META-INF/neoforge.mods.toml").decode())
        meta["mods"][0]["version"] = "<release>"
        for dependency in meta["dependencies"][MOD_ID]:
            if dependency["modId"] in ("minecraft", "neoforge"):
                dependency["versionRange"] = "<targets>"
    return payload, meta


def grouped_jars(matrix, files, version):
    groups = {}
    for target, path in zip(matrix, files, strict=True):
        game = target["minecraft"]
        games = next((g for g in COMPATIBLE_GAMES if game in g), (game,))
        if target["loader"] == "neoforge" and games[0] == "26.1":
            # 26.1.2 compiles different Mixin annotation encodings; keep exact equality.
            games = ("26.1.2",) if game == "26.1.2" else ("26.1", "26.1.1")
        with ZipFile(path) as jar:
            entries = {name: jar.read(name) for name in jar.namelist() if not name.endswith("/")}
        groups.setdefault((target["loader"], games), []).append((target, path, entries))
    result = []
    for members in groups.values():
        members.sort(key=lambda m: tuple(map(int, m[0]["minecraft"].split("."))))
        first, source, entries = members[0]
        baseline = comparable_payload(entries, first)
        for target, _, contents in members[1:]:
            if comparable_payload(contents, target) != baseline:
                raise ValueError(f"Shared jar payload differs: {first['target']} and {target['target']}; split the group")
        games = [member[0]["minecraft"] for member in members]
        label = games[0] if len(games) == 1 else f"{games[0]}-{games[-1]}"
        target = dict(first, target=f"{label}-{first['loader']}", minecraft=label,
                      game_versions="\n".join(games),
                      file=f"afk-cinematics-{version}+mc{label}-{first['loader']}.jar")
        changes = {}
        if len(games) > 1:
            if first["loader"] == "fabric":
                meta = json.loads(entries["fabric.mod.json"])
                meta["version"] = f"{version}+mc{target['target']}"
                meta["depends"]["minecraft"] = games
                changes["fabric.mod.json"] = (json.dumps(meta, indent=2) + "\n").encode()
            else:
                intervals = []
                for member, _, _ in members:
                    minimum = member["neoforge_range"][1:-2]
                    match = re.fullmatch(r"(\d+(?:\.\d+)*)\.\d+(?:-beta)?", minimum)
                    if not match:
                        raise ValueError(f"Unsupported NeoForge version: {minimum}")
                    line = list(map(int, match[1].split(".")))
                    line[-1] += 1
                    # Exclude the next line's prereleases as well as its stable builds.
                    upper = ".".join(map(str, line)) + ".0-alpha"
                    intervals.append(f"[{minimum},{upper})")
                target["neoforge_range"] = ",".join(intervals)
                text = entries["META-INF/neoforge.mods.toml"].decode()
                replacements = {
                    f'version = "{version}+mc{first["target"]}"': f'version = "{version}+mc{target["target"]}"',
                    f'versionRange = "[{first["minecraft"]}]"': 'versionRange = "' + ",".join(f"[{g}]" for g in games) + '"',
                    f'versionRange = "{first["neoforge_range"]}"': f'versionRange = "{target["neoforge_range"]}"',
                }
                for old, new in replacements.items():
                    if text.count(old) != 1:
                        raise ValueError(f"Unexpected metadata in {source.name}: {old}")
                    text = text.replace(old, new)
                changes["META-INF/neoforge.mods.toml"] = text.encode()
        result.append((target, source, changes))
    return result


def bundle(root, version, sha, incoming, output):
    version_key(version)
    if version != current_version(root):
        raise ValueError("Requested version differs from gradle.properties")
    notes = section((root / "CHANGELOG.md").read_text(encoding="utf-8"), version)
    matrix = targets(root, version)
    license_text = (root / "LICENSE.md").read_text(encoding="utf-8")
    if "SPDX-License-Identifier: GPL-3.0-only" not in license_text:
        raise ValueError("Expected GPL-3.0-only source license")
    # Validate the entire set before producing anything eligible for publication.
    files = []
    for target in matrix:
        matches = list(incoming.rglob(target["file"]))
        if len(matches) != 1:
            raise ValueError(f"Expected exactly one {target['file']}, found {len(matches)}")
        validate_jar(matches[0], target, version, license_text)
        files.append(matches[0])
    packaged = grouped_jars(matrix, files, version)
    output.mkdir(parents=True, exist_ok=False)
    for target, path, changes in packaged:
        destination = output / target["file"]
        if not changes:
            shutil.copyfile(path, destination)
        else:
            with ZipFile(path) as original, ZipFile(destination, "w") as jar:
                for entry in original.infolist():
                    jar.writestr(entry, changes.get(entry.filename, original.read(entry)))
        validate_jar(destination, target, version, license_text)
    source = f"afk-cinematics-{version}-source.zip"
    subprocess.run(["git", "archive", "--format=zip", f"--prefix=afk-cinematics-{version}/",
                    f"--output={str((output / source).resolve())}", sha], cwd=root, check=True)
    checksums = "".join(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n"
                        for path in sorted(output.iterdir()))
    (output / "SHA256SUMS").write_text(checksums, encoding="utf-8")
    notes += (f"\n\n[Source and build instructions](https://github.com/{REPOSITORY}/tree/{sha})"
              f"\n\n[Complete source archive](https://github.com/{REPOSITORY}/releases/download/v{version}/{source})\n")
    (output / "release-notes.md").write_text(notes, encoding="utf-8")
    return [target for target, _, _ in packaged]


def publish_github(version, sha, kind, bundle_dir, downloaded):
    version_key(version)
    if not re.fullmatch(r"[a-f0-9]{40}", sha) or kind not in ("release", "alpha", "beta"):
        raise ValueError("Invalid release revision or type")
    tag = f"v{version}"
    # List all releases: lookup-by-tag can miss unpublished drafts without a Git ref.
    pages = json.loads(subprocess.check_output(
        ["gh", "api", "--paginate", "--slurp", f"repos/{REPOSITORY}/releases?per_page=100"], text=True))
    if any(item["tag_name"] == tag for page in pages for item in page):
        raise ValueError("A release or draft already exists; inspect it without overwriting it")
    refs = json.loads(subprocess.check_output(
        ["gh", "api", f"repos/{REPOSITORY}/git/matching-refs/tags/{tag}"], text=True))
    if any(ref["ref"] == f"refs/tags/{tag}" for ref in refs):
        raise ValueError("The release tag already exists; do not rewrite it")
    assets = sorted(bundle_dir.glob("*.jar")) + sorted(bundle_dir.glob("*-source.zip"))
    assets.append(bundle_dir / "SHA256SUMS")
    command = ["gh", "release", "create", tag, *map(str, assets), "--repo", REPOSITORY,
               "--target", sha, "--title", f"AFK Cinematics {version}",
               "--notes-file", str(bundle_dir / "release-notes.md"), "--draft", "--latest=false"]
    if kind != "release":
        command.append("--prerelease")
    subprocess.run(command, check=True)
    subprocess.run(["gh", "release", "download", tag, "--repo", REPOSITORY,
                    "--dir", str(downloaded)], check=True)
    if (downloaded / "SHA256SUMS").read_bytes() != (bundle_dir / "SHA256SUMS").read_bytes():
        raise ValueError("Downloaded manifest differs from the validated release bundle")
    subprocess.run(["sha256sum", "--check", "SHA256SUMS"], cwd=downloaded, check=True)
    subprocess.run(["gh", "release", "edit", tag, "--repo", REPOSITORY, "--draft=false",
                    "--latest=" + str(kind == "release").lower()], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    prep = commands.add_parser("prepare")
    prep.add_argument("version")
    verify = commands.add_parser("bundle")
    verify.add_argument("version")
    verify.add_argument("run_id")
    publish = commands.add_parser("publish-github", help="Publish only after explicit release authorization")
    publish.add_argument("version")
    args = parser.parse_args()
    root = Path.cwd()
    if args.command == "prepare":
        prepare(root, args.version)
        return
    if args.command == "publish-github":
        publish_github(args.version, os.environ["RELEASE_SHA"], os.environ["RELEASE_KIND"],
                       Path("release"), Path("downloaded"))
        return
    if not args.run_id.isdecimal():
        raise ValueError("The Build run ID must be numeric")
    sha = os.environ["RELEASE_SHA"]
    run = json.loads(subprocess.check_output(
        ["gh", "api", f"repos/{REPOSITORY}/actions/runs/{args.run_id}"], text=True))
    validate_run(run, sha)
    matrix = bundle(root, args.version, sha, Path("artifacts/download"), Path("artifacts/release"))
    kind = "release" if "-" not in args.version else ("alpha" if "-alpha." in args.version else "beta")
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as out:
        out.write("matrix=" + json.dumps({"include": matrix}) + "\n")
        out.write(f"kind={kind}\n")
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as out:
        out.write(f"Validated {len(matrix)} jars for **{args.version}**, commit `{sha}`, Build run {args.run_id}.\n\n")
        out.write((Path("artifacts/release") / "release-notes.md").read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()
