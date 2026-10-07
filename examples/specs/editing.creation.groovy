[
    groups: [
        'commands': [
            [
                title: 'Create a language with a generator',
                kind: 'cli',
                code: '''mops create language sample.language --with-generator''',
            ],
            [
                title: 'Create a Java solution',
                kind: 'cli',
                code: '''mops create solution sample.solution --usage-preset java''',
            ],
            [
                title: 'Create a test solution',
                kind: 'cli',
                code: '''mops create solution sample.tests --usage-preset java-tests''',
            ],
            [
                title: 'Create a devkit',
                kind: 'cli',
                code: '''mops create devkit sample.devkit''',
            ],
            [
                title: 'Create an embedded generator',
                kind: 'cli',
                code: '''mops create generator --language sample.language --alias main''',
            ],
            [
                title: 'Create a standalone generator',
                kind: 'cli',
                code: '''mops create generator --language sample.language --alias main --standalone''',
            ],
            [
                title: 'Create a model relative to a module',
                kind: 'cli',
                code: '''mops create model .main --module sample.solution''',
            ],
            [
                title: 'Create a model with one file per root',
                kind: 'cli',
                code: '''mops create model .main --module sample.solution --file-per-root''',
            ],
            [
                title: 'Preview creation',
                kind: 'cli',
                code: '''mops create solution sample.solution --usage-preset java --dry-run''',
            ],
        ],
    ],
]
