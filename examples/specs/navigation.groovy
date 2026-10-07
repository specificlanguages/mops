[
    groups: [
        'commands': [
            [
                title: 'List project modules',
                kind: 'cli',
                code: '''mops list''',
            ],
            [
                title: 'Include platform/library modules',
                kind: 'cli',
                code: '''mops list /''',
            ],
            [
                title: 'List a module\'s models',
                kind: 'cli',
                code: '''mops list sample.solution''',
            ],
            [
                title: 'List model roots',
                kind: 'cli',
                code: '''mops list sample.solution .main''',
            ],
            [
                title: 'Inspect a root and its children',
                kind: 'cli',
                code: '''mops list sample.solution .main Example --depth 2''',
            ],
            [
                title: 'Inspect a node using its stable reference',
                kind: 'cli',
                code: '''mops list 'NODE_REF' --depth 2''',
            ],
            [
                title: 'Show all children without truncation',
                kind: 'cli',
                code: '''mops list 'NODE_REF' --limit 0''',
            ],
            [
                title: 'Count child roles/concepts',
                kind: 'cli',
                code: '''mops list 'NODE_REF' --summary''',
            ],
            [
                title: 'List one containment role',
                kind: 'cli',
                code: '''mops list 'NODE_REF' --role member''',
            ],
            [
                title: 'Export a node subtree as JSON',
                kind: 'cli',
                code: '''mops get node 'NODE_REF'''',
            ],
            [
                title: 'Include its ancestry',
                kind: 'cli',
                code: '''mops get node --ancestry 'NODE_REF'''',
            ],
            [
                title: 'Resolve an ID within a model',
                kind: 'cli',
                code: '''mops get node sample.model 123456789''',
            ],
            [
                title: 'Read the default editor\'s text',
                kind: 'cli',
                code: '''mops render node 'NODE_REF'''',
            ],
            [
                title: 'Render with unloaded languages',
                kind: 'cli',
                code: '''mops render node --allow-reflective 'NODE_REF'''',
            ],
            [
                title: 'Emit clickable MPS URLs in text output',
                kind: 'cli',
                code: '''mops --refs-as-urls list sample.solution .main''',
            ],
        ],
    ],
]
