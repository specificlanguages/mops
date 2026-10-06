[
    groups: [
        'commands': [
            [
                title: 'Find class nodes',
                kind: 'cli',
                code: '''mops find instances jetbrains.mps.baseLanguage.ClassConcept''',
            ],
            [
                title: 'Find only direct instances',
                kind: 'cli',
                code: '''mops find instances --exact jetbrains.mps.baseLanguage.ClassConcept''',
            ],
            [
                title: 'Find named roots by camel-hump/wildcard pattern',
                kind: 'cli',
                code: '''mops find root-by-name '*Builder*'''',
            ],
            [
                title: 'Search roots in one model',
                kind: 'cli',
                code: '''mops find root-by-name '*Builder*' in sample.solution .main''',
            ],
            [
                title: 'Search roots across the repository',
                kind: 'cli',
                code: '''mops find root-by-name '*Builder*' in /''',
            ],
            [
                title: 'Find references to a node',
                kind: 'cli',
                code: '''mops find usages 'NODE_REF'''',
            ],
            [
                title: 'Find all matching node IDs',
                kind: 'cli',
                code: '''mops find node-by-id 123456789 in /''',
            ],
            [
                title: 'Get machine-readable matches',
                kind: 'cli',
                code: '''mops find instances --json jetbrains.mps.baseLanguage.ClassConcept''',
            ],
            [
                title: 'Return every match as a reference',
                kind: 'cli',
                code: '''mops find root-by-name --refs-only --limit 0 '*Builder*'''',
            ],
            [
                title: 'Render every matching root',
                kind: 'cli',
                code: '''mops find root-by-name --refs-only --limit 0 '*Builder*' | while IFS= read -r ref; do mops render node "$ref"; done''',
            ],
        ],
    ],
    snippets: [
        'name-pattern-help': [
            kind: 'cli',
            code: '''mops explain name-pattern''',
        ],
    ],
]
