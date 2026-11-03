# Searching an unbuilt language

mops does not resolve concepts of an editable language that is not built. `find instances <concept>` and other concept
lookups that name such a concept fail with `LANGUAGE_NOT_LOADED` or a not-built error whose remedy is
`mops make module <language>`. mops does not walk the language's structure model to find the concept declaration
instead.

## Why this is out of scope

Concepts resolve through the compiled language runtime. Reading them from the structure model of an unbuilt language
would be a second, unchecked source of concepts next to the runtime. The triage of #45 (2026-10-13) decided against a
structure-model walk for unbuilt languages and kept `make module` as the remedy. A short name offered while a project
language is unbuilt fails rather than guessing, since the unbuilt language could define it too (see the README's
`find instances` section).

## Prior requests

- XOR field test, July 2026: `find instances <fqn>` while developing a language, before building it. Filed only in the
  former local issue tracker.
