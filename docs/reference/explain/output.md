# mops explain output

Generated from `mops explain output` by `./gradlew :cli:updateReference`. Do not edit this page. Back to the
[index](../index.md).

```text
output — how command output separates results, metadata, and diagnostics.

Every command prints three kinds of content, each in a fixed place. Output is
the same whether it is piped or printed on a terminal.

RESULTS
  Results go to stdout: rows, JSON, rendered text, references, and the report
  of an action, such as `made 2 module(s): success`.

  Text output of `find`, `list`, `check`, and `diagnose` is one row per line,
  with tab-separated columns. A summary that is part of the report, such as
  the first row of `diagnose project`, is a result row.

METADATA
  Metadata says what mops understood and what it left out: truncation, an
  empty selection, a summary of counts. In row-shaped text it is a line that
  starts with `#`, never a result, so `grep -v '^#'` leaves only the rows:

    # truncated: showing <shown>; pass --limit 0 for all
    # 1 error, 2 warnings, 0 infos
    # no mops daemons

  A notice about a default the caller did not choose names the option that
  lifts it, as `pass --limit 0 for all` does. In JSON, metadata is fields of
  the object, such as `truncated`. In JSONL, it is a final record with its own
  `type`, such as `{"type":"summary",...}`.

DIAGNOSTICS
  Warnings, errors, and hints go to stderr. Output that has no row shape, such
  as `get node` JSON or a rendered node, prints its hints on stderr too.

MACHINE FORMATS
  `--json`, `--format jsonl`, and `--refs-only` print stdout that can be piped
  without filtering. `--refs-only` prints one serialized node reference per
  line and nothing else, so results pipe straight into `mops get node`. It
  cannot be combined with `--json` and reports truncation on stderr.

NAMES AND REFERENCES
  Text output shows short concept names; `--full-concept` restores the fully
  qualified names. JSON output keeps qualified names regardless.

  Node references print in their serialized form. The global option
  `--refs-as-urls` prints them as MPS URLs in text output instead; JSON output
  is unchanged.

EXIT CODES
  A non-zero exit code means the command did not do what was asked: an error,
  a `check` that found an error-severity problem, or a named selection that
  resolved to nothing, such as a test selection without tests. A search that
  finds nothing succeeds and prints no rows.

SEE ALSO
  node-ref, scope
```
