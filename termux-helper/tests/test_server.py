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

    def run_git(self, *arguments: str) -> str:
        completed_process = subprocess.run(
            ["git", "-C", str(self.repository), *arguments],
            check=True,
            capture_output=True,
            text=True,
        )
        return completed_process.stdout.strip()

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

    def test_returns_latest_commit_metadata_and_diff(self) -> None:
        repository_diff = SERVER.build_repository_diff(self.repository)

        latest_commit = repository_diff["latestCommit"]
        self.assertEqual(latest_commit["subject"], "Initial")
        self.assertEqual(len(latest_commit["id"]), 40)
        self.assertEqual(latest_commit["files"][0]["newPath"], "sample.txt")
        self.assertEqual(latest_commit["files"][0]["status"], "added")
        self.assertEqual(
            [
                line["content"]
                for line in latest_commit["files"][0]["hunks"][0]["lines"]
            ],
            ["first", "second"],
        )

    def test_latest_commit_is_independent_of_working_tree_changes(self) -> None:
        (self.repository / "sample.txt").write_text("working tree\n", encoding="utf-8")

        repository_diff = SERVER.build_repository_diff(self.repository)

        latest_commit_lines = repository_diff["latestCommit"]["files"][0]["hunks"][0]["lines"]
        self.assertEqual(
            [line["content"] for line in latest_commit_lines],
            ["first", "second"],
        )

    def test_returns_commit_history_in_newest_first_order(self) -> None:
        (self.repository / "sample.txt").write_text("second commit\n", encoding="utf-8")
        self.run_git("add", "sample.txt")
        self.run_git("commit", "-m", "Second")

        repository_diff = SERVER.build_repository_diff(self.repository)

        commit_summary_items = repository_diff["commitHistory"]["commits"]
        self.assertEqual(
            [commit_summary["subject"] for commit_summary in commit_summary_items],
            ["Second", "Initial"],
        )
        self.assertIsNone(repository_diff["commitHistory"]["nextOffset"])
        self.assertEqual(commit_summary_items[0]["authorName"], "Diff Viewer Test")
        self.assertTrue(commit_summary_items[0]["authoredAt"])

    def test_paginates_commit_history_twenty_commits_at_a_time(self) -> None:
        for commit_number in range(1, 22):
            (self.repository / "sample.txt").write_text(
                f"commit {commit_number}\n",
                encoding="utf-8",
            )
            self.run_git("add", "sample.txt")
            self.run_git("commit", "-m", f"Commit {commit_number}")

        git_command_runner = SERVER.GitCommandRunner(self.repository)
        first_page = SERVER.build_commit_history_page(git_command_runner, offset=0)
        second_page = SERVER.build_commit_history_page(git_command_runner, offset=20)

        self.assertEqual(len(first_page["commits"]), 20)
        self.assertEqual(first_page["nextOffset"], 20)
        self.assertEqual(len(second_page["commits"]), 2)
        self.assertIsNone(second_page["nextOffset"])

    def test_returns_selected_older_commit_diff(self) -> None:
        initial_commit_id = self.run_git("rev-parse", "HEAD")
        (self.repository / "sample.txt").write_text("second commit\n", encoding="utf-8")
        self.run_git("add", "sample.txt")
        self.run_git("commit", "-m", "Second")

        selected_commit = SERVER.build_commit_diff(
            SERVER.GitCommandRunner(self.repository),
            initial_commit_id,
        )

        self.assertEqual(selected_commit["id"], initial_commit_id)
        self.assertEqual(selected_commit["subject"], "Initial")
        self.assertEqual(selected_commit["files"][0]["status"], "added")

    def test_rejects_invalid_selected_commit_id(self) -> None:
        with self.assertRaisesRegex(SERVER.GitCommandError, "Invalid commit ID"):
            SERVER.build_commit_diff(
                SERVER.GitCommandRunner(self.repository),
                "HEAD~1",
            )

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
