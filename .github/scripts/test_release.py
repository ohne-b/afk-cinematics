import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile

import release


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "gradle.properties").write_text("mod_version=0.1.0-alpha.1\n")
        (self.root / "CHANGELOG.md").write_text("# Changelog\n\n## Unreleased\n\n- A change (#1).\n")

    def test_prepare_preserves_notes_and_other_properties(self):
        config = self.root / "gradle.properties"
        config.write_text(config.read_text() + "fabric_loader=0.20.1\n")
        release.prepare(self.root, "0.1.0-beta.1")
        self.assertEqual(release.current_version(self.root), "0.1.0-beta.1")
        self.assertIn("fabric_loader=0.20.1", config.read_text())
        self.assertEqual(release.section((self.root / "CHANGELOG.md").read_text(), "0.1.0-beta.1"), "- A change (#1).")

    def test_first_release_can_use_current_unpublished_version(self):
        release.prepare(self.root, "0.1.0-alpha.1")
        self.assertIn("## 0.1.0-alpha.1", (self.root / "CHANGELOG.md").read_text())

    def test_invalid_versions_and_downgrades_do_not_edit(self):
        for value in ("1.0", "01.0.0", "0.0.9", "1.0.0; echo secret", "1.0.0\nkey=value", "../1.0.0", "1.0.0-rc.01"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                release.prepare(self.root, value)
            self.assertEqual(release.current_version(self.root), "0.1.0-alpha.1")

    def test_missing_or_unreviewed_notes_fail(self):
        for text in ("## Unreleased\n", "## Unreleased\n\nTODO: notes\n"):
            (self.root / "CHANGELOG.md").write_text(text)
            with self.assertRaises(ValueError):
                release.prepare(self.root, "0.1.0")

    def test_stable_notes_do_not_include_other_version_sections(self):
        self.assertEqual(release.section("## Unreleased\n\n## 1.0.0\n\nStable.\n\n## 1.0.0-rc.1\n\nRC.\n", "1.0.0"), "Stable.")

    def test_run_provenance_rejects_wrong_or_incomplete_builds(self):
        run = {"repository": {"full_name": release.REPOSITORY}, "head_repository": {"full_name": release.REPOSITORY},
               "path": ".github/workflows/build.yml", "event": "workflow_dispatch", "head_branch": "main",
               "head_sha": "a" * 40, "status": "completed", "conclusion": "success"}
        release.validate_run(run, "a" * 40)
        with self.assertRaises(ValueError):
            release.validate_run(dict(run, event="push"), "a" * 40)
        for key, value in {"repository": {"full_name": "other/repo"}, "head_repository": {},
                           "event": "pull_request", "head_branch": "feature/test", "path": "other.yml",
                           "head_sha": "b" * 40, "status": "in_progress", "conclusion": "failure"}.items():
            with self.subTest(key=key), self.assertRaises(ValueError):
                release.validate_run(dict(run, **{key: value}), "a" * 40)

    def target(self, loader, game="1.21.11", neo="21.11.48"):
        config = self.root / "versions" / f"{game}-{loader}" / "gradle.properties"
        config.parent.mkdir(parents=True, exist_ok=True)
        config.write_text(f"minecraft_version={game}\nneoforge_version={neo}\n")
        return next(t for t in release.targets(self.root, "0.1.0") if t["target"] == f"{game}-{loader}")

    def test_each_target_has_exact_metadata_and_dependency(self):
        fabric = self.target("fabric")
        neo = self.target("neoforge")
        self.assertEqual(fabric["file"], "afk-cinematics-0.1.0+mc1.21.11-fabric.jar")
        self.assertIn("modrinth:P7dR8mSH", fabric["dependencies"])
        self.assertEqual(neo["dependencies"], "")
        self.assertEqual(neo["java"], 21)

    def test_fabric_jar_rejects_wrong_version_license_and_test_leakage(self):
        target = self.target("fabric")
        valid = {"id": release.MOD_ID, "version": "0.1.0+mc1.21.11-fabric", "environment": "client",
                 "license": "GPL-3.0-only", "depends": {"minecraft": "1.21.11"}}
        cases = [(valid, None, True), (dict(valid, version="0.0.9"), None, False),
                 (dict(valid, license="MIT"), None, False), (valid, "dev/ohneb/GameTest.class", False)]
        for index, (meta, extra, accepted) in enumerate(cases):
            path = self.root / f"{index}.jar"
            with ZipFile(path, "w") as jar:
                jar.writestr("fabric.mod.json", json.dumps(meta))
                jar.writestr("LICENSE.md", "license\r\n")
                if extra:
                    jar.writestr(extra, "fixture")
            if accepted:
                release.validate_jar(path, target, "0.1.0", "license\n")
            else:
                with self.assertRaises(ValueError):
                    release.validate_jar(path, target, "0.1.0", "license\n")

    def test_neoforge_jar_checks_exact_game_requirement(self):
        target = self.target("neoforge")
        for game in ("[1.21.11]", "[1.21,)"):
            path = self.root / "neo.jar"
            with ZipFile(path, "w") as jar:
                jar.writestr("LICENSE.md", "license")
                jar.writestr("META-INF/neoforge.mods.toml", f'''license = "GPL-3.0-only"
[[mods]]
modId = "{release.MOD_ID}"
version = "0.1.0+mc1.21.11-neoforge"
[[dependencies.{release.MOD_ID}]]
modId = "neoforge"
versionRange = "[21.11.48,)"
side = "CLIENT"
[[dependencies.{release.MOD_ID}]]
modId = "minecraft"
versionRange = "{game}"
side = "CLIENT"
''')
            if game == "[1.21.11]":
                release.validate_jar(path, target, "0.1.0", "license")
            else:
                with self.assertRaises(ValueError):
                    release.validate_jar(path, target, "0.1.0", "license")

    def test_missing_artifact_fails_before_bundle_is_written(self):
        release.prepare(self.root, "0.1.0")
        self.target("fabric")
        (self.root / "LICENSE.md").write_text("SPDX-License-Identifier: GPL-3.0-only")
        out = self.root / "out"
        with self.assertRaises(ValueError):
            release.bundle(self.root, "0.1.0", "a" * 40, self.root / "missing", out)
        self.assertFalse(out.exists())

    def write_group_member(self, target, overrides=None):
        entries = {"LICENSE.md": b"SPDX-License-Identifier: GPL-3.0-only\n",
                   "Camera.class": b"same compiled code", "assets/logo.png": b"same resource"}
        version = "0.1.0+mc" + target["target"]
        if target["loader"] == "fabric":
            entries["fabric.mod.json"] = json.dumps({"id": release.MOD_ID, "version": version,
                "environment": "client", "license": "GPL-3.0-only",
                "depends": {"minecraft": target["minecraft"], "fabricloader": ">=0.19.3", "java": ">=21"},
                "mixins": ["camera.mixins.json"]}).encode()
            entries["META-INF/MANIFEST.MF"] = ("Manifest-Version: 1.0\r\nFabric-Mapping-Namespace: intermediary\r\n"
                f"Fabric-Minecraft-Version: {target['minecraft']}\r\n\r\n").encode()
        else:
            entries["META-INF/neoforge.mods.toml"] = f'''license = "GPL-3.0-only"
[[mods]]
modId = "{release.MOD_ID}"
version = "{version}"
[[dependencies.{release.MOD_ID}]]
modId = "minecraft"
versionRange = "[{target['minecraft']}]"
side = "CLIENT"
[[dependencies.{release.MOD_ID}]]
modId = "neoforge"
versionRange = "{target['neoforge_range']}"
side = "CLIENT"
'''.encode()
        entries.update(overrides or {})
        path = self.root / "download" / target["file"]
        path.parent.mkdir(exist_ok=True)
        with ZipFile(path, "w") as jar:
            for name, contents in entries.items():
                jar.writestr(name, contents)
        return path

    def test_grouped_bundle_keeps_code_and_exact_game_and_loader_requirements(self):
        release.prepare(self.root, "0.1.0")
        license_text = "SPDX-License-Identifier: GPL-3.0-only\n"
        (self.root / "LICENSE.md").write_text(license_text)
        for loader in ("fabric", "neoforge"):
            for game, neo in (("1.21", "21.0.167"), ("1.21.1", "21.1.265")):
                self.write_group_member(self.target(loader, game, neo))
        out = self.root / "bundle"
        def archive(command, **kwargs):
            Path(next(a.removeprefix("--output=") for a in command if a.startswith("--output="))).write_bytes(b"source")
        with patch("release.subprocess.run", side_effect=archive):
            matrix = release.bundle(self.root, "0.1.0", "a" * 40, self.root / "download", out)
        self.assertEqual(len(matrix), 2)
        for target in matrix:
            self.assertEqual(target["game_versions"], "1.21\n1.21.1")
            self.assertEqual(target["minecraft"], "1.21-1.21.1")
            release.validate_jar(out / target["file"], target, "0.1.0", license_text)
            with ZipFile(out / target["file"]) as jar:
                self.assertEqual(jar.read("Camera.class"), b"same compiled code")
                self.assertEqual(jar.read("assets/logo.png"), b"same resource")
                if target["loader"] == "fabric":
                    meta = json.loads(jar.read("fabric.mod.json"))
                    self.assertEqual(meta["depends"]["minecraft"], ["1.21", "1.21.1"])
                    self.assertEqual(meta["depends"]["fabricloader"], ">=0.19.3")
                    self.assertEqual(meta["mixins"], ["camera.mixins.json"])
                else:
                    self.assertEqual(target["neoforge_range"], "[21.0.167,21.1.0-alpha),[21.1.265,21.2.0-alpha)")

    def test_grouping_rejects_changed_code_resources_and_manifest_contracts(self):
        first = self.target("fabric", "1.21")
        second = self.target("fabric", "1.21.1")
        first_path = self.write_group_member(first)
        with ZipFile(self.write_group_member(second)) as jar:
            changed_meta = json.loads(jar.read("fabric.mod.json"))
        changed_meta["depends"]["java"] = ">=25"
        cases = ({"Camera.class": b"different code"}, {"assets/logo.png": b"different resource"},
                 {"META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\r\nFabric-Mapping-Namespace: official\r\n"},
                 {"fabric.mod.json": json.dumps(changed_meta).encode()})
        for change in cases:
            with self.subTest(change=change), self.assertRaisesRegex(ValueError, "payload differs"):
                release.grouped_jars([first, second], [first_path, self.write_group_member(second, change)], "0.1.0")

    def test_neoforge_2612_keeps_its_separate_annotation_build(self):
        matrix = [self.target("neoforge", game, neo) for game, neo in
                  (("26.1", "26.1.0.19-beta"), ("26.1.1", "26.1.1.12-beta"), ("26.1.2", "26.1.2.124"))]
        groups = release.grouped_jars(matrix, [self.write_group_member(t) for t in matrix], "0.1.0")
        self.assertEqual([t["game_versions"] for t, _, _ in groups], ["26.1\n26.1.1", "26.1.2"])

    def test_complete_bundle_copies_only_primary_jars_and_archives_pinned_source(self):
        release.prepare(self.root, "0.1.0")
        target = self.target("fabric")
        license_text = "SPDX-License-Identifier: GPL-3.0-only\n"
        (self.root / "LICENSE.md").write_text(license_text)
        incoming = self.root / "download"
        incoming.mkdir()
        jar_path = incoming / target["file"]
        with ZipFile(jar_path, "w") as jar:
            jar.writestr("LICENSE.md", license_text)
            jar.writestr("fabric.mod.json", json.dumps({"id": release.MOD_ID,
                "version": "0.1.0+mc1.21.11-fabric", "environment": "client", "license": "GPL-3.0-only",
                "depends": {"minecraft": "1.21.11"}}))
        (incoming / "unrelated-sources.jar").write_bytes(b"not a release file")
        out = self.root / "bundle"

        def archive(command, **kwargs):
            self.assertEqual(command[-1], "a" * 40)
            self.assertEqual(kwargs["cwd"], self.root)
            self.assertTrue(kwargs["check"])
            path = Path(next(arg.removeprefix("--output=") for arg in command if arg.startswith("--output=")))
            with ZipFile(path, "w") as source:
                source.writestr("LICENSE.md", license_text)

        with patch("release.subprocess.run", side_effect=archive):
            self.assertEqual(release.bundle(self.root, "0.1.0", "a" * 40, incoming, out), [target])
        self.assertEqual((out / jar_path.name).read_bytes(), jar_path.read_bytes())
        self.assertFalse((out / "unrelated-sources.jar").exists())
        self.assertEqual(len((out / "SHA256SUMS").read_text().splitlines()), 2)
        self.assertIn("/tree/" + "a" * 40, (out / "release-notes.md").read_text())
        # Ambiguous downloads must fail before replacing any release files.
        (incoming / "duplicate").mkdir()
        (incoming / "duplicate" / target["file"]).write_bytes(jar_path.read_bytes())
        with self.assertRaises(ValueError):
            release.bundle(self.root, "0.1.0", "a" * 40, incoming, self.root / "other")

    def test_existing_draft_without_tag_performs_no_publication_writes(self):
        # The matching draft is on a later API page and has no Git tag at all.
        pages = [[{"tag_name": "v0.0.9", "draft": False}],
                 [{"tag_name": "v0.1.0", "draft": True}]]
        with patch("release.subprocess.check_output", return_value=json.dumps(pages)) as read, \
                patch("release.subprocess.run") as write:
            with self.assertRaisesRegex(ValueError, "draft already exists"):
                release.publish_github("0.1.0", "a" * 40, "release", self.root, self.root / "download")
            self.assertIn("--paginate", read.call_args.args[0])
            self.assertIn("--slurp", read.call_args.args[0])
            write.assert_not_called()

    def test_release_lookup_failure_performs_no_publication_writes(self):
        with patch("release.subprocess.check_output", side_effect=subprocess.CalledProcessError(1, "gh")), \
                patch("release.subprocess.run") as write:
            with self.assertRaises(subprocess.CalledProcessError):
                release.publish_github("0.1.0", "a" * 40, "release", self.root, self.root / "download")
            write.assert_not_called()

    def test_existing_tag_performs_no_publication_writes(self):
        with patch("release.subprocess.check_output", side_effect=["[[]]", '[{"ref":"refs/tags/v0.1.0"}]']), \
                patch("release.subprocess.run") as write:
            with self.assertRaisesRegex(ValueError, "tag already exists"):
                release.publish_github("0.1.0", "a" * 40, "release", self.root, self.root / "download")
            write.assert_not_called()

    def test_downloaded_manifest_must_match_validated_bundle_before_publication(self):
        (self.root / "SHA256SUMS").write_text("validated manifest\n")
        downloaded = self.root / "download"
        downloaded.mkdir()
        (downloaded / "SHA256SUMS").write_text("older draft manifest\n")
        with patch("release.subprocess.check_output", side_effect=["[[]]", "[]"]), \
                patch("release.subprocess.run") as run:
            with self.assertRaisesRegex(ValueError, "manifest differs"):
                release.publish_github("0.1.0", "a" * 40, "release", self.root, downloaded)
            self.assertEqual([c.args[0][2] for c in run.call_args_list], ["create", "download"])

    def test_publication_waits_for_downloaded_checksum_validation(self):
        (self.root / "SHA256SUMS").write_text("validated manifest\n")
        downloaded = self.root / "download"
        downloaded.mkdir()
        (downloaded / "SHA256SUMS").write_bytes((self.root / "SHA256SUMS").read_bytes())
        with patch("release.subprocess.check_output", side_effect=["[[]]", "[]"]), \
                patch("release.subprocess.run", side_effect=[None, None, subprocess.CalledProcessError(1, "sha256sum")]) as run:
            with self.assertRaises(subprocess.CalledProcessError):
                release.publish_github("0.1.0", "a" * 40, "release", self.root, downloaded)
            self.assertEqual(len(run.call_args_list), 3)
            self.assertEqual(run.call_args.args[0], ["sha256sum", "--check", "SHA256SUMS"])
            self.assertTrue(run.call_args.kwargs["check"])


if __name__ == "__main__":
    unittest.main()
