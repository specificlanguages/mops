[
    'root-names': [
        group: 'nodes',
        title: 'List root names and references',
        kind: 'groovy',
        code: '''project.read { mops.lookup.requireModel('sample.model').rootNodes.collect { [name: it.properties['name'], node: it] } }''',
        verify: '''
            assert result == [[name: 'Example', node: fixture.root]]
        ''',
    ],
    'property': [
        group: 'nodes',
        title: 'Read a node property',
        kind: 'groovy',
        code: '''project.read { mops.lookup.requireNode('NODE_REF').properties['name'] }''',
        verify: '''
            assert result == 'Example'
        ''',
        output: 'text',
    ],
    'find-root': [
        group: 'nodes',
        title: 'Find a root with an exact name',
        kind: 'groovy',
        code: '''project.read { mops.lookup.requireModel('sample.model').rootNodes.find { it.properties['name'] == 'Example' } }''',
        verify: '''
            assert result == fixture.root
        ''',
        output: 'text',
    ],
    'descendants': [
        group: 'nodes',
        title: 'List containment descendants',
        kind: 'groovy',
        code: '''project.read { mops.lookup.requireNode('NODE_REF').descendants.collect { [concept: it.concept.qualifiedName, node: it] } }''',
        verify: '''
            assert result.size() > 0
            assert result.every { it.concept && it.node }
            assert result.any { it.node == fixture.method }
        ''',
    ],
    'ancestors': [
        group: 'nodes',
        title: 'List ancestors',
        kind: 'groovy',
        code: '''project.read { mops.lookup.requireNode('NODE_REF').ancestors }''',
        verify: '''
            assert result == []
        ''',
    ],
    'property-names': [
        group: 'nodes',
        title: 'List available property names',
        kind: 'groovy',
        code: '''project.read { mops.lookup.requireNode('NODE_REF').concept.properties.collect { it.name } }''',
        verify: '''
            assert 'name' in result
        ''',
    ],
    'child-roles': [
        group: 'nodes',
        title: 'List child roles',
        kind: 'groovy',
        code: '''project.read { mops.lookup.requireNode('NODE_REF').concept.containmentLinks.collect { it.name } }''',
        verify: '''
            assert 'member' in result
        ''',
    ],
    'reference-roles': [
        group: 'nodes',
        title: 'List reference roles',
        kind: 'groovy',
        code: '''project.read { mops.lookup.requireNode('NODE_REF').concept.referenceLinks.collect { it.name } }''',
        verify: '''
            assert 'classifier' in result
        ''',
        output: 'json',
        bindings: ['NODE_REF': 'type'],
    ],
    'single-child': [
        group: 'nodes',
        title: 'Read one child',
        kind: 'groovy',
        code: '''project.read { mops.lookup.requireNode('NODE_REF').child['body'] }''',
        verify: '''
            assert result == fixture.statements
        ''',
        output: 'text',
        bindings: ['NODE_REF': 'method'],
    ],
    'ordered-children': [
        group: 'nodes',
        title: 'Read ordered children in a role',
        kind: 'groovy',
        code: '''project.read { mops.lookup.requireNode('CLASS_REF').children['member'] }''',
        verify: '''
            assert result == fixture.members
        ''',
    ],
    'reference-target': [
        group: 'nodes',
        title: 'Resolve a reference target',
        kind: 'groovy',
        code: '''project.read { mops.lookup.requireNode('NODE_REF').references['classifier']?.targetNode }''',
        verify: '''
            assert result == fixture.old
        ''',
        output: 'text',
        bindings: ['NODE_REF': 'type'],
    ],
    'rename': [
        group: 'nodes',
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
    'retarget': [
        group: 'nodes',
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
    'clear-child': [
        group: 'nodes',
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
    'reverse-members': [
        group: 'nodes',
        title: 'Reverse ordered members',
        kind: 'groovy',
        code: '''project.command { def n = mops.lookup.requireNode('CLASS_REF'); n.children['member'] = n.children['member'].reverse(); n }''',
        verify: '''
            assert result == fixture.root
            assert new groovy.json.JsonSlurper().parseText(run("project.read { mops.lookup.requireNode('CLASS_REF').children['member'] }")) == fixture.members.reverse()
        ''',
        output: 'text',
    ],
    'create-root': [
        group: 'nodes',
        title: 'Create a class root',
        kind: 'groovy',
        code: '''project.command { def m = mops.lookup.requireModel('sample.model'); def n = m.createNode(mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.ClassConcept')); n.properties['name'] = 'Example'; m.addRootNode(n); n }''',
        verify: '''
            assert run("project.read { mops.lookup.requireNode('${result}').properties['name'] }") == 'Example'
        ''',
        output: 'text',
    ],
    'create-solution-model': [
        group: 'nodes',
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
    'render': [
        group: 'nodes',
        title: 'Render a node',
        kind: 'groovy',
        code: '''def n = project.read { mops.lookup.requireNode('NODE_REF') }; n.render()''',
        verify: '''
            assert result.contains('Example')
        ''',
        output: 'text',
    ],
    'runtime-help': [
        group: 'nodes',
        title: 'Inspect a runtime object\'s API',
        kind: 'groovy',
        code: '''project.read { help(mops.lookup.requireNode('NODE_REF')) }''',
        verify: '''
            assert result.contains('SNode')
        ''',
        output: 'text',
    ],
    'instances': [
        group: 'guidance',
        kind: 'groovy',
        code: '''project.read { def found = []; mops.search.eachInstanceOf(mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.ClassConcept'), project.scope) { found << [name: it.properties['name'], node: it] }; found }''',
        verify: '''
            assert result.any { it == [name: 'Example', node: fixture.root] }
        ''',
    ],
    'usages': [
        group: 'guidance',
        kind: 'groovy',
        code: '''project.read { def found = []; mops.search.eachUsageOf(mops.lookup.requireNode('NODE_REF'), project.scope) { found << [role: it.link.name, source: it.sourceNode] }; found }''',
        verify: '''
            assert result == [[role: 'classifier', source: fixture.type]]
        ''',
        output: 'json',
        bindings: ['NODE_REF': 'old'],
    ],
    'run-file': [
        group: 'guidance',
        kind: 'cli',
        code: '''mops code run task.groovy''',
    ],
    'node-api': [
        group: 'guidance',
        kind: 'cli',
        code: '''mops code help SNode''',
    ],
    'run-stdin': [
        group: 'guidance',
        kind: 'shell',
        code: '''printf '%s\\n' 'project.read { project.projectModules.collect { it.moduleName } }' | mops code run -''',
    ],
]
