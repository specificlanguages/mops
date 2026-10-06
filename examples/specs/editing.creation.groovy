[
    'create-a-language-with-a-generator': [
        group: 'commands',
        title: 'Create a language with a generator',
        kind: 'cli',
        code: '''mops create language sample.language --with-generator''',
    ],
    'create-a-java-solution': [
        group: 'commands',
        title: 'Create a Java solution',
        kind: 'cli',
        code: '''mops create solution sample.solution --usage-preset java''',
    ],
    'create-a-test-solution': [
        group: 'commands',
        title: 'Create a test solution',
        kind: 'cli',
        code: '''mops create solution sample.tests --usage-preset java-tests''',
    ],
    'create-a-devkit': [
        group: 'commands',
        title: 'Create a devkit',
        kind: 'cli',
        code: '''mops create devkit sample.devkit''',
    ],
    'create-an-embedded-generator': [
        group: 'commands',
        title: 'Create an embedded generator',
        kind: 'cli',
        code: '''mops create generator --language sample.language --alias main''',
    ],
    'create-a-standalone-generator': [
        group: 'commands',
        title: 'Create a standalone generator',
        kind: 'cli',
        code: '''mops create generator --language sample.language --alias main --standalone''',
    ],
    'create-a-model-relative-to-a-module': [
        group: 'commands',
        title: 'Create a model relative to a module',
        kind: 'cli',
        code: '''mops create model .main --module sample.solution''',
    ],
    'create-a-model-with-one-file-per-root': [
        group: 'commands',
        title: 'Create a model with one file per root',
        kind: 'cli',
        code: '''mops create model .main --module sample.solution --file-per-root''',
    ],
    'preview-creation': [
        group: 'commands',
        title: 'Preview creation',
        kind: 'cli',
        code: '''mops create solution sample.solution --usage-preset java --dry-run''',
    ],
]
