[
    'example-1': [
        kind: 'cli',
        code: '''mops list''',
    ],
    'example-2': [
        kind: 'cli',
        code: '''mops list /''',
    ],
    'example-3': [
        kind: 'cli',
        code: '''mops list sample.solution''',
    ],
    'example-4': [
        kind: 'cli',
        code: '''mops list sample.solution .main''',
    ],
    'example-5': [
        kind: 'cli',
        code: '''mops list sample.solution .main Example --depth 2''',
    ],
    'example-6': [
        kind: 'cli',
        code: '''mops list 'NODE_REF' --depth 2''',
    ],
    'example-7': [
        kind: 'cli',
        code: '''mops list 'NODE_REF' --limit 0''',
    ],
    'example-8': [
        kind: 'cli',
        code: '''mops list 'NODE_REF' --summary''',
    ],
    'example-9': [
        kind: 'cli',
        code: '''mops list 'NODE_REF' --role member''',
    ],
    'example-10': [
        kind: 'cli',
        code: '''mops get node 'NODE_REF'''',
    ],
    'example-11': [
        kind: 'cli',
        code: '''mops get node --ancestry 'NODE_REF'''',
    ],
    'example-12': [
        kind: 'cli',
        code: '''mops get node sample.model 123456789''',
    ],
    'example-13': [
        kind: 'cli',
        code: '''mops render node 'NODE_REF'''',
    ],
    'example-14': [
        kind: 'cli',
        code: '''mops render node --allow-reflective 'NODE_REF'''',
    ],
    'example-15': [
        kind: 'cli',
        code: '''mops --refs-as-urls list sample.solution .main''',
    ],
]
