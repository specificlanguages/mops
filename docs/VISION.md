# mops: vision and scope

mops lets coding agents inspect, search, edit, build, check, and test JetBrains MPS projects from a terminal, without
the MPS IDE and without reading `.mps` XML. It is the tool an agent reaches for where it would otherwise reach for
`grep`, `find`, or a text editor on persistence files.

## Who it is for

- **Primary: an agent.** An LLM working in a shell, reading tool output, with no screen. Every command's output is
  written to be read by a model: compact, self-describing, bounded.
- **Secondary: automation.** Scripts and CI jobs that call mops non-interactively, for example to export data or gate a
  build. They consume the machine formats and exit codes, never the reading format, so they rarely conflict with the
  agent; where they would, the machine formats absorb the difference.
- **Third: a human at the terminal.** The same commands must stay usable by a person, but when personas conflict, the
  agent wins and automation comes before the human.
- **The maintainer** of mops is a fourth persona in specs.

Specs (PRDs, issues, ADR user stories) use these four names and no others. User-facing text such as the README, help,
explain pages, and skills addresses the reader directly and names "agent" or "human" only where the distinction matters.

## Principles

These are the decisions the ADRs in `docs/adr/` already encode, stated once:

1. **MPS semantics, not persistence.** mops speaks in modules, models, nodes, concepts, roles, and references as MPS
   defines them, never in XML elements or file layout (ADR-0001).
2. **Fail explicitly, never guess.** A lookup returns the one object it names, or nothing; it never picks among
   candidates, and ambiguity is an error at the point where it occurs. Parsers reject malformed input instead of
   recovering from it (ADR-0001, 0002, 0011, 0012).
3. **Make every implicit choice visible.** When mops narrows, filters, or fills in a default on the caller's behalf, the
   output says so, and a default that quietly drops results is a defect. Defaults express the caller's intent rather
   than MPS producer defaults (ADR-0009). Search scope is the standing example: a search names the scope it searched,
   and scope names mean the same thing in every command and in Code Mode (revises ADR-0003, see #54).
4. **Never silently break a model.** Edits are checked cheaply and every violation is reported (ADR-0004).
5. **Native MPS knowledge transfers.** Someone who knows the MPS API should need few mops-specific concepts, in Code
   Mode above all (ADR-0008, 0012).
6. **Correctness and robustness over speed.** Operations have an enforceable termination boundary (ADR-0013).

## Output contract

Every command's output has three kinds of content, and each has a fixed home:

- **Results go to stdout.** Rows, JSON, rendered text, references, and an action's report ("made 2 modules").
- **Metadata travels with the result, in a shape the format can tell apart from a result.** Metadata is what mops
  understood and what it left out: truncation, the scope searched, the resolved target, "zero matches". In row-shaped
  text it is `#`-prefixed lines on stdout; in JSON it is fields of the envelope; in JSONL it is a final record with its
  own type; free text such as a rendered node has no such shape, so there it goes to stderr. A notice caused by a
  default the caller did not choose names the option that lifts it.
- **Diagnostics go to stderr.** Warnings, errors, and hints.

**Composability is a value**, and automation is its persona. Every data-producing command offers a machine format
(`--json`, `--refs-only`, JSONL) whose stdout can be piped without filtering.

**Content never depends on the terminal.** Output is byte-for-byte the same piped or on a TTY. Presentation may differ,
for example references wrapped in terminal hyperlinks when a console is attached.

**Exit codes report failure.** Non-zero means the command did not do what was asked: an error, a `check` that found a
problem of error severity, or a named selection that resolved to nothing, such as a test selection with no tests or a
check with nothing to check. A search that finds nothing succeeds with empty stdout and a metadata note. A `--fail-on`
option for `check` (`error` today's behaviour, `warning`, `never`) is a later addition.

## Scope

In scope, as capability areas: navigating and listing the repository; getting and rendering nodes; searching for
instances, usages, and concepts; editing nodes with checked operations; creating modules and models; building, checking,
and testing; Code Mode programs over native MPS objects; the daemon that keeps a project open for all of this.

**Commands versus Code Mode** is decided case by case, not by rule. Both are first-class surfaces that share one
implementation (ADR-0008). `find`, `list`, `get`, `render`, and `edit` earn dedicated commands because they are used in
nearly every task, depend on mops-specific machinery such as scopes and references, and need output shaped for an agent
to read. Code Mode is where composition over the native API happens; when a Code Mode program needs a primitive mops
does not expose, exposing that primitive is in scope. Forcing everything into Code Mode is not a goal.

## Out of scope

- **Re-teaching MPS or the JVM.** mops documents its own contracts and extensions. How `reloadFromSource` or `System.gc`
  behave is MPS and JVM documentation.
- **Sandboxing Code Mode.** Programs are trusted (ADR-0008).

Rejected requests, with the reasoning and the issues that asked, are recorded one concept per file in `.out-of-scope/`.

## Triage heuristics

Field-test feedback is evidence, not a specification. Judge each request by:

1. **Does it remove a class of fallback, or one incident?** Eliminating a category of the fallbacks named in the opening
   paragraph is in scope. A patch for one task's hiccup is not; the problem space is too large to cover incident by
   incident.
2. **Is it mops's contract or someone else's documentation?** Gaps in what mops guarantees (threading, lifetimes, output
   shape) are mops's to close; see the re-teaching non-goal above for the rest.
3. **Is it already covered?** Check the codebase by domain concept before specifying; several field-test requests
   describe shipped features under other names.
4. **Build on evidence.** When a message or a document would do, prefer it over a mechanism, and add the mechanism only
   when a later field test shows the message was not enough.
