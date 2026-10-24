#!/usr/bin/env python3
"""Fails when a Kotlin or Markdown file is larger than MAX_BYTES and explains how to split it."""

import sys
from pathlib import Path

MAX_BYTES = 20_000

KOTLIN_ADVICE = """\
  Split the Kotlin file along its responsibilities:
  - Keep one top-level class, object, or interface per file. Move other top-level declarations to their own files.
  - Find groups of functions that work on the same data or serve one feature. Extract each group into its own class,
    and give the class a name that says what it does.
  - Move helpers that only one part of the file uses next to that part, in a separate file.
  - Split a large test class by the behavior it tests: one test class per feature or scenario, with shared fixtures
    and builders in a separate helper file.
  Run the module's tests after the split; detekt and the architecture tests run in `./gradlew check`."""

MARKDOWN_ADVICE = """\
  Split the Markdown file by topic:
  - Move each large section to its own document and replace it with a short summary and a link.
  - Move reference material (long tables, examples, option lists) to a separate reference document.
  - Keep the original file as the entry point that says what each linked document covers."""


def main(paths: list[str]) -> int:
    failed = False
    for name in paths:
        path = Path(name)
        size = path.stat().st_size
        if size <= MAX_BYTES:
            continue
        failed = True
        advice = KOTLIN_ADVICE if path.suffix == ".kt" else MARKDOWN_ADVICE
        print(f"{name}: {size} bytes, limit is {MAX_BYTES} bytes.\n{advice}\n")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
