[
    'example-1': [
        kind: 'cli',
        code: '''mops find instances jetbrains.mps.baseLanguage.ClassConcept''',
    ],
    'example-2': [
        kind: 'cli',
        code: '''mops find instances --exact jetbrains.mps.baseLanguage.ClassConcept''',
    ],
    'example-3': [
        kind: 'cli',
        code: '''mops find root-by-name '*Builder*'''',
    ],
    'example-4': [
        kind: 'cli',
        code: '''mops find root-by-name '*Builder*' in sample.solution .main''',
    ],
    'example-5': [
        kind: 'cli',
        code: '''mops find root-by-name '*Builder*' in /''',
    ],
    'example-6': [
        kind: 'cli',
        code: '''mops find usages 'NODE_REF'''',
    ],
    'example-7': [
        kind: 'cli',
        code: '''mops find node-by-id 123456789 in /''',
    ],
    'example-8': [
        kind: 'cli',
        code: '''mops find instances --json jetbrains.mps.baseLanguage.ClassConcept''',
    ],
    'example-9': [
        kind: 'cli',
        code: '''mops find root-by-name --refs-only --limit 0 '*Builder*'''',
    ],
    'example-10': [
        kind: 'cli',
        code: '''mops find root-by-name --refs-only --limit 0 '*Builder*' \\| while IFS= read -r ref; do mops render node "$ref"; done''',
    ],
    'example-11': [
        kind: 'cli',
        code: '''mops explain name-pattern''',
    ],
]
