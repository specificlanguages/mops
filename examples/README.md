# Example specifications

Edit `specs/<topic>.groovy` to change a snippet and its expectations. Edit `pages/<topic>.md` for prose and placement;
`{{table:group|Column heading}}` inserts a table containing every example in that group, in catalog order. Each member
supplies the row's `title` and `code`. `{{id}}` slots insert a named snippet into prose or a code fence. The generated
pages serve both `mops examples` and the bundled skill references.

A topic file returns a Groovy map. For example:

```groovy
[
    property: [
        group: 'nodes',
        title: 'Read a node property',
        kind: 'groovy',
        code: '''project.read { mops.lookup.requireNode('NODE_REF').properties['name'] }''',
        output: 'text',
        verify: '''assert result == 'Example' ''',
    ],
]
```

Groups are scoped to their topic. A template containing `{{table:nodes|Groovy program}}` includes the example above and
all other `nodes` members; the template does not enumerate individual rows. The generated JSON manifest includes each
example's `group`.

Groovy snippets require either `verify` assertions or an `untested` fixture requirement. The generator rejects missing
expectations, unused specs, and unknown slots or groups. `ExamplesCommandTest` parses every `cli` and `shell` catalog
entry against the command tree, including commands within pipelines, and validates documented JSON edit batches. These
are syntax/schema checks; CLI recipes are not executed end to end.

The integration runner starts one daemon. Each case gets fresh models from `fixture.groovy`. Its returned map supplies
`fixture` and the default substitutions for sample model names and node references. `bindings: [NODE_REF: 'method']`
selects a different fixture value; values absent from the fixture are literal replacements. `before` optionally runs a
preparation program before the snippet.

Assertions run as Groovy on the test JVM. Their bindings are:

- `result`: parsed JSON by default, or trimmed stdout with `output: 'text'`.
- `fixture`: the map of references and initial member order from the fixture program.
- `run(source)`: execute another Code Mode program using the case's substitutions; returns trimmed stdout.
- `before`: stdout from the optional preparation program.
- `modelFile`: the case model's persisted `.mps` file, for save/import assertions.

Each Groovy spec appears separately in JUnit reports as `<topic>/<id>`. Adding a spec to a displayed group adds a row
and a test without changing the template or runner. Specs with `untested` requirements appear as skipped tests.

```sh
./gradlew :cli:updateExamples :cli:checkExamples
./gradlew :cli:smokeTest
```

Commit the catalog, templates, and regenerated skill references together. `checkExamples` verifies the checked-in pages
without starting MPS.

The pre-commit CI job generates the pages before running Markdown lint. `.markdownlint-cli2.yaml` includes the build's
generated pages in every lint run. MD013 limits prose to 120 characters and excludes code blocks and tables globally.
