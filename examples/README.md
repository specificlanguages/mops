# Example specifications

Edit `specs/<topic>.groovy` to change a snippet and its expectations. Edit `pages/<topic>.md` for prose and placement;
`{{id}}` slots expand to the corresponding spec's `code`. The generated pages serve both `mops examples` and the bundled
skill references.

A topic file returns a Groovy map. For example:

```groovy
[
    property: [
        kind: 'groovy',
        code: '''project.read { mops.lookup.requireNode('NODE_REF').properties['name'] }''',
        output: 'text',
        verify: '''assert result == 'Example' ''',
    ],
]
```

Groovy snippets require either `verify` assertions or an `untested` fixture requirement. The generator rejects missing
expectations, unused specs, and unknown slots. `cli` and `shell` snippets receive the offline command/schema checks in
`ExamplesCommandTest`.

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

Each Groovy spec appears separately in JUnit reports as `<topic>/<id>`. Adding a spec and its Markdown slot adds a test
without changing the runner. Specs with `untested` requirements appear as skipped tests.

```sh
./gradlew :cli:updateExamples :cli:checkExamples
./gradlew :cli:smokeTest
```

Commit the catalog, templates, and regenerated skill references together. `checkExamples` verifies the checked-in pages
without starting MPS.
