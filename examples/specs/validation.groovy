[
    groups: [
        'commands': [
            [
                title: 'Make selected modules and their dependencies',
                kind: 'cli',
                code: '''mops make module sample.language sample.solution''',
            ],
            [
                title: 'Make all generatable project modules',
                kind: 'cli',
                code: '''mops make project''',
            ],
            [
                title: 'Check a model, including typesystem/checking rules',
                kind: 'cli',
                code: '''mops check model sample.model''',
            ],
            [
                title: 'Check selected modules',
                kind: 'cli',
                code: '''mops check module sample.language sample.solution''',
            ],
            [
                title: 'Check the project',
                kind: 'cli',
                code: '''mops check project''',
            ],
            [
                title: 'Emit all model findings as JSON lines',
                kind: 'cli',
                code: '''mops check model sample.model --format jsonl --limit 0''',
            ],
            [
                title: 'Diagnose unloaded project modules',
                kind: 'cli',
                code: '''mops diagnose project''',
            ],
            [
                title: 'Include loaded modules in diagnosis',
                kind: 'cli',
                code: '''mops diagnose project --all''',
            ],
            [
                title: 'Inspect a module\'s load/dependency problem',
                kind: 'cli',
                code: '''mops diagnose module sample.language''',
            ],
            [
                title: 'Start/reuse the daemon and check connectivity',
                kind: 'cli',
                code: '''mops daemon ping''',
            ],
            [
                title: 'Inspect all known daemons',
                kind: 'cli',
                code: '''mops daemon status --all''',
            ],
            [
                title: 'Stop the project\'s daemon',
                kind: 'cli',
                code: '''mops daemon stop''',
            ],
            [
                title: 'Stop all known daemons',
                kind: 'cli',
                code: '''mops daemon stop --all''',
            ],
        ],
        'tests': [
            [
                title: 'Build and run all project tests',
                kind: 'cli',
                code: '''mops test''',
            ],
            [
                title: 'Build and run all tests in a module',
                kind: 'cli',
                code: '''mops test sample.tests --json''',
            ],
            [
                title: 'Build and run all tests in a model',
                kind: 'cli',
                code: '''mops test 'sample.tests.tests@tests' --json''',
            ],
            [
                title: 'Run one root test case',
                kind: 'cli',
                code: '''mops test sample.tests .tests MyCase''',
            ],
            [
                title: 'Run a root test case using compiled classes',
                kind: 'cli',
                code: '''mops test 'NODE_REF' --no-build --json''',
            ],
            [
                title: 'Set the overall test deadline (seconds)',
                kind: 'cli',
                code: '''mops test --timeout 60''',
            ],
        ],
    ],
    snippets: [
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
        'disable-deadline': [
            kind: 'cli',
            code: '''mops code run --timeout 0 task.groovy''',
        ],
    ],
]
