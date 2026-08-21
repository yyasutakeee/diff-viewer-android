#!/usr/bin/env python3
"""Read-only localhost service that exposes structured Git working-tree diffs."""

from __future__ import annotations

import argparse
import hmac
import json
import os
from dataclasses import dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re
import shlex
import subprocess
from typing import Any
from urllib.parse import urlparse


MAX_UNTRACKED_FILE_BYTES = 1_000_000
HUNK_HEADER_PATTERN = re.compile(
    r"^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@(?: (.*))?$"
)


class GitCommandError(RuntimeError):
    """Raised when a read-only Git command cannot complete."""


@dataclass(frozen=True)
class GitCommandRunner:
    repository: Path

    def run(self, *arguments: str, text: bool = True) -> str | bytes:
        completed_process = subprocess.run(
            ["git", "-C", str(self.repository), *arguments],
            check=False,
            capture_output=True,
            text=text,
        )
        if completed_process.returncode != 0:
            error_output = completed_process.stderr
            if isinstance(error_output, bytes):
                error_output = error_output.decode("utf-8", errors="replace")
            raise GitCommandError(error_output.strip() or "Git command failed")
        return completed_process.stdout


def build_repository_diff(repository: Path) -> dict[str, Any]:
    resolved_repository = repository.expanduser().resolve(strict=True)
    git_command_runner = GitCommandRunner(resolved_repository)
    repository_root_output = git_command_runner.run("rev-parse", "--show-toplevel")
    repository_root = Path(str(repository_root_output).strip()).resolve(strict=True)
    if repository_root != resolved_repository:
        raise GitCommandError("Configured path must be the Git repository root")

    branch_output = str(git_command_runner.run("branch", "--show-current")).strip()
    if not branch_output:
        branch_output = str(
            git_command_runner.run("rev-parse", "--short", "HEAD")
        ).strip()

    unstaged_patch = str(
        git_command_runner.run(
            "-c",
            "core.quotePath=false",
            "diff",
            "--no-ext-diff",
            "--no-color",
            "--unified=3",
        )
    )
    staged_patch = str(
        git_command_runner.run(
            "-c",
            "core.quotePath=false",
            "diff",
            "--cached",
            "--no-ext-diff",
            "--no-color",
            "--unified=3",
        )
    )
    untracked_output = git_command_runner.run(
        "ls-files",
        "--others",
        "--exclude-standard",
        "-z",
        text=False,
    )
    latest_commit_output = str(
        git_command_runner.run("log", "-1", "--format=%H%x00%s")
    ).rstrip("\n")
    latest_commit_id, latest_commit_subject = latest_commit_output.split("\0", maxsplit=1)
    latest_commit_patch = str(
        git_command_runner.run(
            "-c",
            "core.quotePath=false",
            "show",
            "--format=",
            "--diff-merges=first-parent",
            "--find-renames",
            "--no-ext-diff",
            "--no-color",
            "--unified=3",
            "HEAD",
        )
    )

    return {
        "repository": str(repository_root),
        "branch": branch_output,
        "latestCommit": {
            "id": latest_commit_id,
            "subject": latest_commit_subject,
            "files": parse_unified_diff(latest_commit_patch),
        },
        "sections": [
            {"kind": "unstaged", "files": parse_unified_diff(unstaged_patch)},
            {"kind": "staged", "files": parse_unified_diff(staged_patch)},
            {
                "kind": "untracked",
                "files": build_untracked_file_diffs(
                    repository_root,
                    bytes(untracked_output),
                ),
            },
        ],
    }


def parse_unified_diff(patch: str) -> list[dict[str, Any]]:
    file_diffs: list[dict[str, Any]] = []
    current_file_diff: dict[str, Any] | None = None
    current_hunk: dict[str, Any] | None = None
    old_line_number: int | None = None
    new_line_number: int | None = None

    for raw_line in patch.splitlines():
        if raw_line.startswith("diff --git "):
            current_file_diff = create_file_diff(raw_line)
            file_diffs.append(current_file_diff)
            current_hunk = None
            continue

        if current_file_diff is None:
            continue

        if raw_line.startswith("rename from "):
            current_file_diff["oldPath"] = raw_line.removeprefix("rename from ")
            current_file_diff["status"] = "renamed"
            continue
        if raw_line.startswith("rename to "):
            current_file_diff["newPath"] = raw_line.removeprefix("rename to ")
            current_file_diff["status"] = "renamed"
            continue
        if raw_line.startswith("new file mode "):
            current_file_diff["status"] = "added"
            continue
        if raw_line.startswith("deleted file mode "):
            current_file_diff["status"] = "deleted"
            continue
        if raw_line.startswith("Binary files ") or raw_line.startswith("GIT binary patch"):
            current_file_diff["isBinary"] = True
            continue

        hunk_match = HUNK_HEADER_PATTERN.match(raw_line)
        if hunk_match:
            old_line_number = int(hunk_match.group(1))
            new_line_number = int(hunk_match.group(3))
            current_hunk = {
                "header": raw_line,
                "lines": [],
            }
            current_file_diff["hunks"].append(current_hunk)
            continue

        if current_hunk is None:
            continue

        line_data, old_line_number, new_line_number = parse_diff_line(
            raw_line,
            old_line_number,
            new_line_number,
        )
        current_hunk["lines"].append(line_data)

    return file_diffs


def create_file_diff(diff_header: str) -> dict[str, Any]:
    header_parts = shlex.split(diff_header)
    if len(header_parts) < 4:
        raise GitCommandError(f"Unsupported diff header: {diff_header}")
    old_path = remove_diff_prefix(header_parts[2], "a/")
    new_path = remove_diff_prefix(header_parts[3], "b/")
    return {
        "oldPath": old_path,
        "newPath": new_path,
        "status": "modified",
        "isBinary": False,
        "hunks": [],
    }


def remove_diff_prefix(path: str, prefix: str) -> str:
    return path[len(prefix) :] if path.startswith(prefix) else path


def parse_diff_line(
    raw_line: str,
    old_line_number: int | None,
    new_line_number: int | None,
) -> tuple[dict[str, Any], int | None, int | None]:
    if raw_line.startswith("+"):
        line_data = build_line_data("addition", raw_line[1:], None, new_line_number)
        return line_data, old_line_number, increment_line_number(new_line_number)
    if raw_line.startswith("-"):
        line_data = build_line_data("deletion", raw_line[1:], old_line_number, None)
        return line_data, increment_line_number(old_line_number), new_line_number
    if raw_line.startswith(" "):
        line_data = build_line_data(
            "context",
            raw_line[1:],
            old_line_number,
            new_line_number,
        )
        return (
            line_data,
            increment_line_number(old_line_number),
            increment_line_number(new_line_number),
        )
    return (
        build_line_data("meta", raw_line, None, None),
        old_line_number,
        new_line_number,
    )


def build_line_data(
    kind: str,
    content: str,
    old_line_number: int | None,
    new_line_number: int | None,
) -> dict[str, Any]:
    return {
        "kind": kind,
        "content": content,
        "oldLine": old_line_number,
        "newLine": new_line_number,
    }


def increment_line_number(line_number: int | None) -> int | None:
    return line_number + 1 if line_number is not None else None


def build_untracked_file_diffs(
    repository_root: Path,
    untracked_output: bytes,
) -> list[dict[str, Any]]:
    file_diffs: list[dict[str, Any]] = []
    for encoded_path in filter(None, untracked_output.split(b"\0")):
        relative_path = encoded_path.decode("utf-8", errors="surrogateescape")
        file_path = (repository_root / relative_path).resolve(strict=True)
        if repository_root not in file_path.parents:
            raise GitCommandError("Untracked path escaped the repository root")
        file_diffs.append(build_untracked_file_diff(relative_path, file_path))
    return file_diffs


def build_untracked_file_diff(relative_path: str, file_path: Path) -> dict[str, Any]:
    file_size = file_path.stat().st_size
    file_diff: dict[str, Any] = {
        "oldPath": None,
        "newPath": relative_path,
        "status": "untracked",
        "isBinary": False,
        "hunks": [],
    }
    if file_size > MAX_UNTRACKED_FILE_BYTES:
        file_diff["isBinary"] = True
        return file_diff

    content = file_path.read_bytes()
    if b"\0" in content:
        file_diff["isBinary"] = True
        return file_diff

    text = content.decode("utf-8", errors="replace")
    lines = text.splitlines()
    if not lines and not text:
        return file_diff

    file_diff["hunks"] = [
        {
            "header": f"@@ -0,0 +1,{len(lines)} @@",
            "lines": [
                build_line_data("addition", line, None, index)
                for index, line in enumerate(lines, start=1)
            ],
        }
    ]
    return file_diff


def create_request_handler(
    repository: Path,
    access_token: str,
) -> type[BaseHTTPRequestHandler]:
    class DiffRequestHandler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            if urlparse(self.path).path != "/api/v1/diff":
                self.send_json(HTTPStatus.NOT_FOUND, {"error": "Not found"})
                return
            authorization = self.headers.get("Authorization", "")
            expected_authorization = f"Bearer {access_token}"
            if not hmac.compare_digest(authorization, expected_authorization):
                self.send_json(HTTPStatus.UNAUTHORIZED, {"error": "Unauthorized"})
                return
            try:
                response = build_repository_diff(repository)
            except (GitCommandError, OSError) as error:
                self.send_json(
                    HTTPStatus.INTERNAL_SERVER_ERROR,
                    {"error": str(error)},
                )
                return
            self.send_json(HTTPStatus.OK, response)

        def send_json(self, status: HTTPStatus, payload: dict[str, Any]) -> None:
            response_body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(response_body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(response_body)

        def log_message(self, format_string: str, *arguments: object) -> None:
            print(f"{self.address_string()} - {format_string % arguments}")

    return DiffRequestHandler


def parse_arguments() -> argparse.Namespace:
    argument_parser = argparse.ArgumentParser(description=__doc__)
    argument_parser.add_argument(
        "--repository",
        required=True,
        type=Path,
        help="Exact Git repository root to expose",
    )
    argument_parser.add_argument("--port", type=int, default=8765)
    return argument_parser.parse_args()


def main() -> None:
    arguments = parse_arguments()
    access_token = os.environ.get("DIFF_VIEWER_TOKEN", "")
    if not access_token:
        raise SystemExit("DIFF_VIEWER_TOKEN must be set")
    request_handler = create_request_handler(arguments.repository, access_token)
    server = ThreadingHTTPServer(("127.0.0.1", arguments.port), request_handler)
    print(f"Diff Viewer helper listening on http://127.0.0.1:{arguments.port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("Stopping Diff Viewer helper")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
