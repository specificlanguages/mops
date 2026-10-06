[
    'find-class-nodes': [
        group: 'commands',
        title: 'Find class nodes',
        kind: 'cli',
        code: '''mops find instances jetbrains.mps.baseLanguage.ClassConcept''',
    ],
    'find-only-direct-instances': [
        group: 'commands',
        title: 'Find only direct instances',
        kind: 'cli',
        code: '''mops find instances --exact jetbrains.mps.baseLanguage.ClassConcept''',
    ],
    'find-named-roots-by-camel-hump-wildcard-pattern': [
        group: 'commands',
        title: 'Find named roots by camel-hump/wildcard pattern',
        kind: 'cli',
        code: '''mops find root-by-name '*Builder*'''',
    ],
    'search-roots-in-one-model': [
        group: 'commands',
        title: 'Search roots in one model',
        kind: 'cli',
        code: '''mops find root-by-name '*Builder*' in sample.solution .main''',
    ],
    'search-roots-across-the-repository': [
        group: 'commands',
        title: 'Search roots across the repository',
        kind: 'cli',
        code: '''mops find root-by-name '*Builder*' in /''',
    ],
    'find-references-to-a-node': [
        group: 'commands',
        title: 'Find references to a node',
        kind: 'cli',
        code: '''mops find usages 'NODE_REF'''',
    ],
    'find-all-matching-node-ids': [
        group: 'commands',
        title: 'Find all matching node IDs',
        kind: 'cli',
        code: '''mops find node-by-id 123456789 in /''',
    ],
    'get-machine-readable-matches': [
        group: 'commands',
        title: 'Get machine-readable matches',
        kind: 'cli',
        code: '''mops find instances --json jetbrains.mps.baseLanguage.ClassConcept''',
    ],
    'return-every-match-as-a-reference': [
        group: 'commands',
        title: 'Return every match as a reference',
        kind: 'cli',
        code: '''mops find root-by-name --refs-only --limit 0 '*Builder*'''',
    ],
    'render-every-matching-root': [
        group: 'commands',
        title: 'Render every matching root',
        kind: 'cli',
        code: '''mops find root-by-name --refs-only --limit 0 '*Builder*' | while IFS= read -r ref; do mops render node "$ref"; done''',
    ],
    'name-pattern-help': [
        group: 'guidance',
        kind: 'cli',
        code: '''mops explain name-pattern''',
    ],
]
