"""Prepare reviewed release notes and verify CI artifacts; never publish from here."""

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
        if properties(config)["minecraft_version"] != minecraft:
            raise ValueError(f"Mismatched target properties: {target}")
        result.append({"target": target, "minecraft": minecraft, "loader": loader,
                       "java": 21 if minecraft.startswith("1.21") else 25,
                       "file": f"afk-cinematics-{version}+mc{target}.jar",
                       "dependencies": "fabric-api(required){modrinth:P7dR8mSH}{curseforge:306612}"
                       if loader == "fabric" else ""})
    if not result:
        raise ValueError("No Minecraft targets found")
    return result


def validate_run(run, sha):
    if not (run.get("repository", {}).get("full_name") == REPOSITORY
            and run.get("head_repository", {}).get("full_name") == REPOSITORY
            and run.get("path") == ".github/workflows/build.yml"
            and run.get("event") == "push" and run.get("head_branch") == "main"
            and run.get("head_sha") == sha and run.get("status") == "completed"
            and run.get("conclusion") == "success"):
        raise ValueError("Use a successful Build push run for this exact main commit")


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
        if target["loader"] == "fabric":
            meta = json.loads(jar.read("fabric.mod.json"))
            valid = (meta["id"] == MOD_ID and meta["version"] == expected
                     and meta["environment"] == "client" and meta["license"] == "GPL-3.0-only"
                     and meta["depends"]["minecraft"] == target["minecraft"]
                     and "META-INF/neoforge.mods.toml" not in names)
        else:
            meta = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode())
            minecraft = next(d for d in meta["dependencies"][MOD_ID] if d["modId"] == "minecraft")
            valid = (meta["mods"][0]["modId"] == MOD_ID and meta["mods"][0]["version"] == expected
                     and meta["license"] == "GPL-3.0-only"
                     and minecraft["versionRange"] == f"[{target['minecraft']}]"
                     and minecraft["side"] == "CLIENT" and "fabric.mod.json" not in names)
        if not valid:
            raise ValueError(f"Wrong version, loader or license in {path.name}")


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
    output.mkdir(parents=True, exist_ok=False)
    for path in files:
        shutil.copyfile(path, output / path.name)
    source = f"afk-cinematics-{version}-source.zip"
    subprocess.run(["git", "archive", "--format=zip", f"--prefix=afk-cinematics-{version}/",
                    f"--output={str((output / source).resolve())}", sha], cwd=root, check=True)
    checksums = "".join(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n"
                        for path in sorted(output.iterdir()))
    (output / "SHA256SUMS").write_text(checksums, encoding="utf-8")
    notes += (f"\n\n[Source and build instructions](https://github.com/{REPOSITORY}/tree/{sha})"
              f"\n\n[Complete source archive](https://github.com/{REPOSITORY}/releases/download/v{version}/{source})\n")
    (output / "release-notes.md").write_text(notes, encoding="utf-8")
    return matrix


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    prep = commands.add_parser("prepare")
    prep.add_argument("version")
    verify = commands.add_parser("bundle")
    verify.add_argument("version")
    verify.add_argument("run_id")
    args = parser.parse_args()
    root = Path.cwd()
    if args.command == "prepare":
        prepare(root, args.version)
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
