[
    'apply-a-saved-batch': [
        group: 'commands',
        title: 'Apply a saved batch',
        kind: 'cli',
        code: '''mops edit model --file edits.json''',
    ],
    'require-all-concepts-to-be-checkable': [
        group: 'commands',
        title: 'Require all concepts to be checkable',
        kind: 'cli',
        code: '''mops edit model --file edits.json --constraints strict''',
    ],
    'apply-while-reporting-constraint-violations': [
        group: 'commands',
        title: 'Apply while reporting constraint violations',
        kind: 'cli',
        code: '''mops edit model --file edits.json --constraints advisory''',
    ],
    'discover-subtree-creation-notation': [
        group: 'commands',
        title: 'Discover subtree creation notation',
        kind: 'cli',
        code: '''mops explain edit.addRoot''',
    ],
    'discover-replacement-notation': [
        group: 'commands',
        title: 'Discover replacement notation',
        kind: 'cli',
        code: '''mops explain edit.replace''',
    ],
    'discover-wrapping-notation': [
        group: 'commands',
        title: 'Discover wrapping notation',
        kind: 'cli',
        code: '''mops explain edit.wrap''',
    ],
    'discover-copying-notation': [
        group: 'commands',
        title: 'Discover copying notation',
        kind: 'cli',
        code: '''mops explain edit.copyAsChild''',
    ],
    'export-the-batch-schema': [
        group: 'commands',
        title: 'Export the batch schema',
        kind: 'cli',
        code: '''mops explain --schema edit''',
    ],
    'rename-node': [
        group: 'guidance',
        kind: 'shell',
        code: '''printf '%s\\n' '{"operations":[{"op":"setProperty","target":"NODE_REF","name":"name","value":"Renamed"}]}' | mops edit model''',
    ],
    'copy-root': [
        group: 'guidance',
        kind: 'shell',
        code: '''printf '%s\\n' '{"operations":[{"op":"copyAsRoot","model":"MODEL_REF","source":"SOURCE_REF","as":"copy"},{"op":"setProperty","target":"$copy","name":"name","value":"RegressionTest"}]}' | mops edit model''',
    ],
    'copy-member': [
        group: 'guidance',
        kind: 'shell',
        code: '''printf '%s\\n' '{"operations":[{"op":"copyAsChild","target":"CLASS_REF","source":"MEMBER_REF","role":"member","position":"last"}]}' | mops edit model''',
    ],
]
