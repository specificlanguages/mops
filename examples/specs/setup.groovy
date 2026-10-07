[
    groups: [
        'commands': [
            [
                title: 'Discover Gradle-configured MPS/Java and write wrappers',
                kind: 'cli',
                code: '''mops wrapper''',
            ],
            [
                title: 'Write a wrapper for one project',
                kind: 'cli',
                code: '''mops wrapper --project-root ../my-project''',
            ],
            [
                title: 'Select a project explicitly',
                kind: 'cli',
                code: '''mops --project-root ../my-project list''',
            ],
            [
                title: 'Supply an MPS distribution directly',
                kind: 'cli',
                code: '''mops --mps-home /path/to/MPS list''',
            ],
            [
                title: 'Discover CLI commands',
                kind: 'cli',
                code: '''mops --help''',
            ],
            [
                title: 'Discover a command\'s options',
                kind: 'cli',
                code: '''mops create solution --help''',
            ],
            [
                title: 'Discover Groovy helpers',
                kind: 'cli',
                code: '''mops code help''',
            ],
            [
                title: 'Inspect helper signatures and access requirements',
                kind: 'cli',
                code: '''mops code help mops.parsing.java''',
            ],
            [
                title: 'Inspect native node members and extensions',
                kind: 'cli',
                code: '''mops code help SNode''',
            ],
            [
                title: 'Read edit notation',
                kind: 'cli',
                code: '''mops explain edit''',
            ],
            [
                title: 'Read search scope syntax',
                kind: 'cli',
                code: '''mops explain scope''',
            ],
            [
                title: 'Locate bundled agent guidance',
                kind: 'cli',
                code: '''mops skill path''',
            ],
        ],
    ],
]
