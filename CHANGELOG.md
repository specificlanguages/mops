# Changelog

## 0.2.0 - Unreleased

- Added `mops list-models`, which discovers `.mps` files and file-per-root `.model` metadata files and emits a model-ID-to-location JSON map.
- Renamed the CLI entry point from `mps-decompress` to `mops decompress`.
- Changed XML output to use `<empty />` syntax for empty elements to improve readability and reduce token usage.
- Deferred `mops help <command>` support as a useful future CLI usability improvement.
