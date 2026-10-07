[
    groups: [
        'nodes': [
            [
                title: 'List root names and references',
                kind: 'groovy',
                code: '''project.read { mops.lookup.requireModel('sample.model').rootNodes.collect { [name: it.properties['name'], node: it] } }''',
                verify: '''
                    assert result == [[name: 'Example', node: fixture.root]]
                ''',
            ],
            [
                title: 'Read a node property',
                kind: 'groovy',
                code: '''project.read { mops.lookup.requireNode('NODE_REF').properties['name'] }''',
                verify: '''
                    assert result == 'Example'
                ''',
                output: 'text',
            ],
            [
                title: 'Find a root with an exact name',
                kind: 'groovy',
                code: '''project.read { mops.lookup.requireModel('sample.model').rootNodes.find { it.properties['name'] == 'Example' } }''',
                verify: '''
                    assert result == fixture.root
                ''',
                output: 'text',
            ],
            [
                title: 'List containment descendants',
                kind: 'groovy',
                code: '''project.read { mops.lookup.requireNode('NODE_REF').descendants.collect { [concept: it.concept.qualifiedName, node: it] } }''',
                verify: '''
                    assert result.size() > 0
                    assert result.every { it.concept && it.node }
                    assert result.any { it.node == fixture.method }
                ''',
            ],
            [
                title: 'List ancestors',
                kind: 'groovy',
                code: '''project.read { mops.lookup.requireNode('NODE_REF').ancestors }''',
                verify: '''
                    assert result == []
                ''',
            ],
            [
                title: 'List available property names',
                kind: 'groovy',
                code: '''project.read { mops.lookup.requireNode('NODE_REF').concept.properties.collect { it.name } }''',
                verify: '''
                    assert 'name' in result
                ''',
            ],
            [
                title: 'List child roles',
                kind: 'groovy',
                code: '''project.read { mops.lookup.requireNode('NODE_REF').concept.containmentLinks.collect { it.name } }''',
                verify: '''
                    assert 'member' in result
                ''',
            ],
            [
                title: 'List reference roles',
                kind: 'groovy',
                code: '''project.read { mops.lookup.requireNode('NODE_REF').concept.referenceLinks.collect { it.name } }''',
                verify: '''
                    assert 'classifier' in result
                ''',
                output: 'json',
                bindings: ['NODE_REF': 'type'],
            ],
            [
                title: 'Read one child',
                kind: 'groovy',
                code: '''project.read { mops.lookup.requireNode('NODE_REF').child['body'] }''',
                verify: '''
                    assert result == fixture.statements
                ''',
                output: 'text',
                bindings: ['NODE_REF': 'method'],
            ],
            [
                title: 'Read ordered children in a role',
                kind: 'groovy',
                code: '''project.read { mops.lookup.requireNode('CLASS_REF').children['member'] }''',
                verify: '''
                    assert result == fixture.members
                ''',
            ],
            [
                title: 'Resolve a reference target',
                kind: 'groovy',
                code: '''project.read { mops.lookup.requireNode('NODE_REF').references['classifier']?.targetNode }''',
                verify: '''
                    assert result == fixture.old
                ''',
                output: 'text',
                bindings: ['NODE_REF': 'type'],
            ],
            [
                title: 'Rename a node',
                kind: 'groovy',
                code: '''project.command { def n = mops.lookup.requireNode('NODE_REF'); n.properties['name'] = 'Renamed'; n }''',
                verify: '''
                    assert result == fixture.root
                    assert run("project.read { mops.lookup.requireNode('NODE_REF').properties['name'] }") == 'Renamed'
                    assert modelFile.text.contains('Renamed')
                ''',
                output: 'text',
            ],
            [
                title: 'Change a reference target',
                kind: 'groovy',
                code: '''project.command { def n = mops.lookup.requireNode('NODE_REF'); n.references['classifier'] = mops.lookup.requireNode('TARGET_REF'); n }''',
                verify: '''
                    assert result == fixture.type
                    assert run("project.read { mops.lookup.requireNode('NODE_REF').references['classifier'].targetNode }") == fixture.replacement
                ''',
                output: 'text',
                bindings: ['NODE_REF': 'type'],
            ],
            [
                title: 'Clear a child role',
                kind: 'groovy',
                code: '''project.command { def n = mops.lookup.requireNode('NODE_REF'); n.child['body'] = null; n }''',
                verify: '''
                    assert result == fixture.method
                    assert run("project.read { mops.lookup.requireNode('NODE_REF').child['body'] == null }") == 'true'
                ''',
                output: 'text',
                bindings: ['NODE_REF': 'method'],
            ],
            [
                title: 'Reverse ordered members',
                kind: 'groovy',
                code: '''project.command { def n = mops.lookup.requireNode('CLASS_REF'); n.children['member'] = n.children['member'].reverse(); n }''',
                verify: '''
                    assert result == fixture.root
                    assert new groovy.json.JsonSlurper().parseText(run("project.read { mops.lookup.requireNode('CLASS_REF').children['member'] }")) == fixture.members.reverse()
                ''',
                output: 'text',
            ],
            [
                title: 'Create a class root',
                kind: 'groovy',
                code: '''project.command { def m = mops.lookup.requireModel('sample.model'); def n = m.createNode(mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.ClassConcept')); n.properties['name'] = 'Example'; m.addRootNode(n); n }''',
                verify: '''
                    assert run("project.read { mops.lookup.requireNode('${result}').properties['name'] }") == 'Example'
                ''',
                output: 'text',
            ],
            [
                title: 'Create a solution and model',
                kind: 'groovy',
                code: '''project.command { project.createSolution('sample.solution', [usagePreset: 'java']).createModel('sample.model') }''',
                verify: '''
                    assert result.contains('sample.model')
                    assert run("project.read { mops.lookup.requireModel('sample.model').module.moduleName }") == 'sample.solution'
                ''',
                output: 'text',
                bindings: ['sample.model': 'sample.model'],
            ],
            [
                title: 'Render a node',
                kind: 'groovy',
                code: '''def n = project.read { mops.lookup.requireNode('NODE_REF') }; n.render()''',
                verify: '''
                    assert result.contains('Example')
                ''',
                output: 'text',
            ],
            [
                title: 'Inspect a runtime object\'s API',
                kind: 'groovy',
                code: '''project.read { help(mops.lookup.requireNode('NODE_REF')) }''',
                verify: '''
                    assert result.contains('SNode')
                ''',
                output: 'text',
            ],
        ],
    ],
    snippets: [
        'instances': [
            kind: 'groovy',
            code: '''project.read { def found = []; mops.search.eachInstanceOf(mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.ClassConcept'), project.scope) { found << [name: it.properties['name'], node: it] }; found }''',
            verify: '''
                assert result.any { it == [name: 'Example', node: fixture.root] }
            ''',
        ],
        'usages': [
            kind: 'groovy',
            code: '''project.read { def found = []; mops.search.eachUsageOf(mops.lookup.requireNode('NODE_REF'), project.scope) { found << [role: it.link.name, source: it.sourceNode] }; found }''',
            verify: '''
                assert result == [[role: 'classifier', source: fixture.type]]
            ''',
            output: 'json',
            bindings: ['NODE_REF': 'old'],
        ],
        'run-file': [
            kind: 'cli',
            code: '''mops code run task.groovy''',
        ],
        'node-api': [
            kind: 'cli',
            code: '''mops code help SNode''',
        ],
        'run-stdin': [
            kind: 'shell',
            code: '''printf '%s\\n' 'project.read { project.projectModules.collect { it.moduleName } }' | mops code run -''',
        ],
    ],
]
