[
    'example-1': [
        kind: 'cli',
        code: '''mops edit model --file edits.json''',
    ],
    'example-2': [
        kind: 'cli',
        code: '''mops edit model --file edits.json --constraints strict''',
    ],
    'example-3': [
        kind: 'cli',
        code: '''mops edit model --file edits.json --constraints advisory''',
    ],
    'example-4': [
        kind: 'cli',
        code: '''mops explain edit.addRoot''',
    ],
    'example-5': [
        kind: 'cli',
        code: '''mops explain edit.replace''',
    ],
    'example-6': [
        kind: 'cli',
        code: '''mops explain edit.wrap''',
    ],
    'example-7': [
        kind: 'cli',
        code: '''mops explain edit.copyAsChild''',
    ],
    'example-8': [
        kind: 'cli',
        code: '''mops explain --schema edit''',
    ],
    'example-9': [
        kind: 'shell',
        code: '''printf '%s\\n' '{"operations":[{"op":"setProperty","target":"NODE_REF","name":"name","value":"Renamed"}]}' | mops edit model''',
    ],
    'example-10': [
        kind: 'shell',
        code: '''printf '%s\\n' '{"operations":[{"op":"copyAsRoot","model":"MODEL_REF","source":"SOURCE_REF","as":"copy"},{"op":"setProperty","target":"$copy","name":"name","value":"RegressionTest"}]}' | mops edit model''',
    ],
    'example-11': [
        kind: 'shell',
        code: '''printf '%s\\n' '{"operations":[{"op":"copyAsChild","target":"CLASS_REF","source":"MEMBER_REF","role":"member","position":"last"}]}' | mops edit model''',
    ],
]
