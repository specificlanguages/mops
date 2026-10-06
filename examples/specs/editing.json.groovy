[
    groups: [
        'commands': [
            [
                title: 'Apply a saved batch',
                kind: 'cli',
                code: '''mops edit model --file edits.json''',
            ],
            [
                title: 'Require all concepts to be checkable',
                kind: 'cli',
                code: '''mops edit model --file edits.json --constraints strict''',
            ],
            [
                title: 'Apply while reporting constraint violations',
                kind: 'cli',
                code: '''mops edit model --file edits.json --constraints advisory''',
            ],
            [
                title: 'Discover subtree creation notation',
                kind: 'cli',
                code: '''mops explain edit.addRoot''',
            ],
            [
                title: 'Discover replacement notation',
                kind: 'cli',
                code: '''mops explain edit.replace''',
            ],
            [
                title: 'Discover wrapping notation',
                kind: 'cli',
                code: '''mops explain edit.wrap''',
            ],
            [
                title: 'Discover copying notation',
                kind: 'cli',
                code: '''mops explain edit.copyAsChild''',
            ],
            [
                title: 'Export the batch schema',
                kind: 'cli',
                code: '''mops explain --schema edit''',
            ],
        ],
    ],
    snippets: [
        'rename-node': [
            kind: 'shell',
            code: '''printf '%s\\n' '{"operations":[{"op":"setProperty","target":"NODE_REF","name":"name","value":"Renamed"}]}' | mops edit model''',
        ],
        'copy-root': [
            kind: 'shell',
            code: '''printf '%s\\n' '{"operations":[{"op":"copyAsRoot","model":"MODEL_REF","source":"SOURCE_REF","as":"copy"},{"op":"setProperty","target":"$copy","name":"name","value":"RegressionTest"}]}' | mops edit model''',
        ],
        'copy-member': [
            kind: 'shell',
            code: '''printf '%s\\n' '{"operations":[{"op":"copyAsChild","target":"CLASS_REF","source":"MEMBER_REF","role":"member","position":"last"}]}' | mops edit model''',
        ],
    ],
]
