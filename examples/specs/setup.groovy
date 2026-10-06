[
    'discover-gradle-configured-mps-java-and-write-wrappers': [
        group: 'commands',
        title: 'Discover Gradle-configured MPS/Java and write wrappers',
        kind: 'cli',
        code: '''mops wrapper''',
    ],
    'write-a-wrapper-for-one-project': [
        group: 'commands',
        title: 'Write a wrapper for one project',
        kind: 'cli',
        code: '''mops wrapper --project-root ../my-project''',
    ],
    'select-a-project-explicitly': [
        group: 'commands',
        title: 'Select a project explicitly',
        kind: 'cli',
        code: '''mops --project-root ../my-project list''',
    ],
    'supply-an-mps-distribution-directly': [
        group: 'commands',
        title: 'Supply an MPS distribution directly',
        kind: 'cli',
        code: '''mops --mps-home /path/to/MPS list''',
    ],
    'discover-cli-commands': [
        group: 'commands',
        title: 'Discover CLI commands',
        kind: 'cli',
        code: '''mops --help''',
    ],
    'discover-a-command-s-options': [
        group: 'commands',
        title: 'Discover a command\'s options',
        kind: 'cli',
        code: '''mops create solution --help''',
    ],
    'discover-groovy-helpers': [
        group: 'commands',
        title: 'Discover Groovy helpers',
        kind: 'cli',
        code: '''mops code help''',
    ],
    'inspect-helper-signatures-and-access-requirements': [
        group: 'commands',
        title: 'Inspect helper signatures and access requirements',
        kind: 'cli',
        code: '''mops code help mops.parsing.java''',
    ],
    'inspect-native-node-members-and-extensions': [
        group: 'commands',
        title: 'Inspect native node members and extensions',
        kind: 'cli',
        code: '''mops code help SNode''',
    ],
    'read-edit-notation': [
        group: 'commands',
        title: 'Read edit notation',
        kind: 'cli',
        code: '''mops explain edit''',
    ],
    'read-search-scope-syntax': [
        group: 'commands',
        title: 'Read search scope syntax',
        kind: 'cli',
        code: '''mops explain scope''',
    ],
    'locate-bundled-agent-guidance': [
        group: 'commands',
        title: 'Locate bundled agent guidance',
        kind: 'cli',
        code: '''mops skill path''',
    ],
]
