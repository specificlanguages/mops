[
    'example-1': [
        kind: 'cli',
        code: '''mops create language sample.language --with-generator''',
    ],
    'example-2': [
        kind: 'cli',
        code: '''mops create solution sample.solution --usage-preset java''',
    ],
    'example-3': [
        kind: 'cli',
        code: '''mops create solution sample.tests --usage-preset java-tests''',
    ],
    'example-4': [
        kind: 'cli',
        code: '''mops create devkit sample.devkit''',
    ],
    'example-5': [
        kind: 'cli',
        code: '''mops create generator --language sample.language --alias main''',
    ],
    'example-6': [
        kind: 'cli',
        code: '''mops create generator --language sample.language --alias main --standalone''',
    ],
    'example-7': [
        kind: 'cli',
        code: '''mops create model .main --module sample.solution''',
    ],
    'example-8': [
        kind: 'cli',
        code: '''mops create model .main --module sample.solution --file-per-root''',
    ],
    'example-9': [
        kind: 'cli',
        code: '''mops create solution sample.solution --usage-preset java --dry-run''',
    ],
]
