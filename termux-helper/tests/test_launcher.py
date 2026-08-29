from __future__ import annotations

from importlib.machinery import SourceFileLoader
import importlib.util
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest


LAUNCHER_PATH = Path(__file__).parents[1] / "diff-viewer"
MODULE_LOADER = SourceFileLoader("diff_viewer_launcher", str(LAUNCHER_PATH))
MODULE_SPEC = importlib.util.spec_from_loader(MODULE_LOADER.name, MODULE_LOADER)
assert MODULE_SPEC is not None
LAUNCHER = importlib.util.module_from_spec(MODULE_SPEC)
sys.modules[MODULE_SPEC.name] = LAUNCHER
MODULE_LOADER.exec_module(LAUNCHER)


class DiffViewerLauncherTests(unittest.TestCase):
    def test_finds_repository_root_from_subdirectory(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository = Path(temporary_directory)
            subprocess.run(
                ["git", "-C", str(repository), "init", "-b", "develop"],
                check=True,
                capture_output=True,
            )
            subdirectory = repository / "nested"
            subdirectory.mkdir()

            repository_root = LAUNCHER.find_repository_root(subdirectory)

            self.assertEqual(repository_root, repository.resolve())

    def test_creates_and_reuses_private_token_file(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            token_file = Path(temporary_directory) / "config" / "diff-viewer" / "token"

            first_token, first_was_created = LAUNCHER.load_or_create_token(token_file)
            second_token, second_was_created = LAUNCHER.load_or_create_token(token_file)

            self.assertTrue(first_was_created)
            self.assertFalse(second_was_created)
            self.assertEqual(first_token, second_token)
            file_mode = stat.S_IMODE(token_file.stat().st_mode)
            self.assertEqual(file_mode, 0o600)


if __name__ == "__main__":
    unittest.main()
