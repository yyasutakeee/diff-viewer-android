from __future__ import annotations

import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SERVER_PATH = Path(__file__).parents[1] / "server.py"
MODULE_SPEC = importlib.util.spec_from_file_location("diff_viewer_server", SERVER_PATH)
assert MODULE_SPEC is not None and MODULE_SPEC.loader is not None
SERVER = importlib.util.module_from_spec(MODULE_SPEC)
sys.modules[MODULE_SPEC.name] = SERVER
MODULE_SPEC.loader.exec_module(SERVER)


class RepositoryDiffTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.repository = Path(self.temporary_directory.name)
        self.run_git("init", "-b", "main")
        self.run_git("config", "user.name", "Diff Viewer Test")
        self.run_git("config", "user.email", "diff-viewer@example.invalid")
        (self.repository / "sample.txt").write_text("first\nsecond\n", encoding="utf-8")
        self.run_git("add", "sample.txt")
        self.run_git("commit", "-m", "Initial")

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def run_git(self, *arguments: str) -> None:
        subprocess.run(
            ["git", "-C", str(self.repository), *arguments],
            check=True,
            capture_output=True,
            text=True,
        )

    def section(self, repository_diff: dict, kind: str) -> dict:
        return next(
            section
            for section in repository_diff["sections"]
            if section["kind"] == kind
        )

    def test_builds_staged_unstaged_and_untracked_sections(self) -> None:
        (self.repository / "sample.txt").write_text("first\nstaged\n", encoding="utf-8")
        self.run_git("add", "sample.txt")
        (self.repository / "sample.txt").write_text(
            "first\nstaged\nunstaged\n",
            encoding="utf-8",
        )
        (self.repository / "new file.txt").write_text("new\ncontent\n", encoding="utf-8")

        repository_diff = SERVER.build_repository_diff(self.repository)

        staged_file = self.section(repository_diff, "staged")["files"][0]
        unstaged_file = self.section(repository_diff, "unstaged")["files"][0]
        untracked_file = self.section(repository_diff, "untracked")["files"][0]
        self.assertEqual(repository_diff["branch"], "main")
        self.assertEqual(staged_file["status"], "modified")
        self.assertEqual(unstaged_file["status"], "modified")
        self.assertEqual(untracked_file["newPath"], "new file.txt")
        self.assertEqual(untracked_file["status"], "untracked")
        self.assertEqual(
            [line["content"] for line in untracked_file["hunks"][0]["lines"]],
            ["new", "content"],
        )

    def test_tracks_line_numbers_and_line_kinds(self) -> None:
        (self.repository / "sample.txt").write_text("first\nreplacement\n", encoding="utf-8")

        repository_diff = SERVER.build_repository_diff(self.repository)

        lines = self.section(repository_diff, "unstaged")["files"][0]["hunks"][0]["lines"]
        deletion = next(line for line in lines if line["kind"] == "deletion")
        addition = next(line for line in lines if line["kind"] == "addition")
        self.assertEqual((deletion["oldLine"], deletion["newLine"]), (2, None))
        self.assertEqual((addition["oldLine"], addition["newLine"]), (None, 2))

    def test_marks_binary_untracked_file_without_returning_content(self) -> None:
        (self.repository / "binary.dat").write_bytes(b"before\0after")

        repository_diff = SERVER.build_repository_diff(self.repository)

        binary_file = self.section(repository_diff, "untracked")["files"][0]
        self.assertTrue(binary_file["isBinary"])
        self.assertEqual(binary_file["hunks"], [])

    def test_rejects_subdirectory_instead_of_repository_root(self) -> None:
        subdirectory = self.repository / "folder"
        subdirectory.mkdir()

        with self.assertRaisesRegex(
            SERVER.GitCommandError,
            "Configured path must be the Git repository root",
        ):
            SERVER.build_repository_diff(subdirectory)


if __name__ == "__main__":
    unittest.main()
