[
    'make': [
        kind: 'groovy',
        code: '''project.make()''',
        untested: 'Requires module generation; excluded from the short recipe run.',
    ],
    'run-tests': [
        kind: 'groovy',
        code: '''def model = project.read { mops.lookup.requireModel('sample.tests.tests@tests') }; def r = mops.testing.run(model, [build: true, timeout: 60]); [successful: r.successful, diagnostics: r.diagnostics, reportPath: r.reportPath]''',
        untested: 'Requires a compiled test-model fixture.',
    ],
    'reload-build': [
        kind: 'groovy',
        code: '''project.command { def r = mops.editing.build.reloadModulesFromDisk(mops.lookup.requireNode('BUILD_PROJECT_REF')); [succeeded: r.succeeded, messages: r.messages.collect { [kind: it.kind, text: it.text, node: it.node] }] }''',
        untested: 'Requires a build-language fixture.',
    ],
    'example-4': [
        kind: 'cli',
        code: '''mops make module sample.language sample.solution''',
    ],
    'example-5': [
        kind: 'cli',
        code: '''mops make project''',
    ],
    'example-6': [
        kind: 'cli',
        code: '''mops check model sample.model''',
    ],
    'example-7': [
        kind: 'cli',
        code: '''mops check module sample.language sample.solution''',
    ],
    'example-8': [
        kind: 'cli',
        code: '''mops check project''',
    ],
    'example-9': [
        kind: 'cli',
        code: '''mops check model sample.model --format jsonl --limit 0''',
    ],
    'example-10': [
        kind: 'cli',
        code: '''mops test''',
    ],
    'example-11': [
        kind: 'cli',
        code: '''mops test sample.tests .tests MyCase''',
    ],
    'example-12': [
        kind: 'cli',
        code: '''mops test 'NODE_REF' --no-build --json''',
    ],
    'example-13': [
        kind: 'cli',
        code: '''mops test --timeout 60''',
    ],
    'example-14': [
        kind: 'cli',
        code: '''mops diagnose project''',
    ],
    'example-15': [
        kind: 'cli',
        code: '''mops diagnose project --all''',
    ],
    'example-16': [
        kind: 'cli',
        code: '''mops diagnose module sample.language''',
    ],
    'example-17': [
        kind: 'cli',
        code: '''mops daemon ping''',
    ],
    'example-18': [
        kind: 'cli',
        code: '''mops daemon status --all''',
    ],
    'example-19': [
        kind: 'cli',
        code: '''mops daemon stop''',
    ],
    'example-20': [
        kind: 'cli',
        code: '''mops daemon stop --all''',
    ],
    'example-21': [
        kind: 'cli',
        code: '''mops code run --timeout 0 task.groovy''',
    ],
]
