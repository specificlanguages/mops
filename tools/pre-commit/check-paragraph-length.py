#!/usr/bin/env python3
"""Fails when a plain-text file has a paragraph with more words than `max_words` in `.markdownlint-cli2.yaml`.

A directory argument stands for the `.txt` files directly in it.

Plain-text counterpart of the markdownlint rule `paragraph-length` in `tools/markdownlint/`. A paragraph is a run of
non-blank lines with the same indentation, so an indented example or a heading above indented text is a paragraph of
its own. A line that starts a list item (`- `, `* `, `1. `) starts a new paragraph. A word is a run of non-whitespace
characters that contains at least one letter or digit.
"""

import re
import sys
from pathlib import Path

CONFIG = Path(__file__).resolve().parents[2] / ".markdownlint-cli2.yaml"
LIST_ITEM = re.compile(r"([-*]|\d+\.) ")
WORD = re.compile(r"\S+")
HAS_LETTER_OR_DIGIT = re.compile(r"[^\W_]")

ADVICE = (
    "Do not add more sentences to this paragraph. Reread it, cut what repeats or no longer matters, and split it only "
    "where the topic really changes, not in the middle. A list may fit enumerations better."
)


def max_words() -> int:
    match = re.search(r"^\s*max_words:\s*(\d+)\s*$", CONFIG.read_text(), re.MULTILINE)
    if match is None:
        raise SystemExit(f"{CONFIG}: max_words is not configured")
    return int(match.group(1))


def paragraphs(lines: list[str]):
    """Yields (first line number, word count) for each paragraph."""
    start, indent, words = None, None, 0
    for number, line in enumerate(lines, start=1):
        text = line.lstrip()
        line_indent = len(line) - len(text)
        if start is not None and (not text or line_indent != indent or LIST_ITEM.match(text)):
            yield start, words
            start, words = None, 0
        if text:
            if start is None:
                start, indent = number, line_indent
            words += sum(1 for word in WORD.findall(text) if HAS_LETTER_OR_DIGIT.search(word))
    if start is not None:
        yield start, words


def text_files(names: list[str]) -> list[Path]:
    """Expands each directory to the `.txt` files directly in it."""
    paths = []
    for name in names:
        path = Path(name)
        paths.extend(sorted(path.glob("*.txt")) if path.is_dir() else [path])
    return paths


def main(names: list[str]) -> int:
    limit = max_words()
    failed = False
    for path in text_files(names):
        for line, words in paragraphs(path.read_text().splitlines()):
            if words > limit:
                failed = True
                print(f"{path}:{line}: {words} words, limit is {limit}. {ADVICE}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
