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
        'concept-by-name': [
            kind: 'groovy',
            code: '''project.read {
    def concept = mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.structure.ClassConcept')
    concept.qualifiedName
}''',
            output: 'text',
            verify: "assert result == 'jetbrains.mps.baseLanguage.structure.ClassConcept'",
        ],
        'missing-concept': [
            kind: 'groovy',
            code: '''project.read {
    mops.lookup.conceptByName('jetbrains.mps.baseLanguage.structure.NoSuchMopsConcept') == null
}''',
            verify: 'assert result == true',
        ],
        'serialize-concept': [
            kind: 'groovy',
            code: '''import org.jetbrains.mps.openapi.persistence.PersistenceFacade
project.read {
    def concept = mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.structure.ClassConcept')
    PersistenceFacade.instance.asString(concept)
}''',
            output: 'text',
            verify: "assert result == 'c:f3061a53-9226-4cc5-a443-f952ceaf5816/1068390468198:jetbrains.mps.baseLanguage.structure.ClassConcept'",
        ],
        'concept-by-id': [
            kind: 'groovy',
            code: '''import org.jetbrains.mps.openapi.persistence.PersistenceFacade
project.read {
    def concept = PersistenceFacade.instance.createConcept(
        'c:f3061a53-9226-4cc5-a443-f952ceaf5816/1068390468198:jetbrains.mps.baseLanguage.structure.ClassConcept')
    [name: concept.qualifiedName, valid: concept.valid]
}''',
            verify: "assert result == [name: 'jetbrains.mps.baseLanguage.structure.ClassConcept', valid: true]",
        ],
        'instances': [
            kind: 'groovy',
            code: '''project.read {
    def concept = mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.structure.Classifier')
    def found = []
    mops.search.eachInstanceOf(concept, project.scope) { node ->
        found << [name: node.properties['name'], node: node]
    }
    found
}''',
            verify: "assert result.any { it == [name: 'Example', node: fixture.root] }",
        ],
        'exact-instances': [
            kind: 'groovy',
            code: '''project.read {
    def concept = mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.structure.Classifier')
    def found = []
    mops.search.eachInstanceOf(concept, project.scope, true) { node -> found << node }
    found
}''',
            verify: 'assert !result.contains(fixture.root)',
        ],
        'instances-by-id': [
            kind: 'groovy',
            code: '''import org.jetbrains.mps.openapi.persistence.PersistenceFacade
project.read {
    def concept = PersistenceFacade.instance.createConcept(
        'c:f3061a53-9226-4cc5-a443-f952ceaf5816/1068390468198:jetbrains.mps.baseLanguage.structure.ClassConcept')
    assert concept.valid
    def found = []
    mops.search.eachInstanceOf(concept, project.scope) { node -> found << node }
    found
}''',
            verify: 'assert result.contains(fixture.root)',
        ],
        'usages': [
            kind: 'groovy',
            code: '''project.read {
    def target = mops.lookup.requireNode('NODE_REF')
    def found = []
    mops.search.eachUsageOf(target, project.scope) { reference ->
        found << [role: reference.link.name, source: reference.sourceNode]
    }
    found
}''',
            bindings: ['NODE_REF': 'old'],
            verify: "assert result == [[role: 'classifier', source: fixture.type]]",
        ],
        'usages-by-role': [
            kind: 'groovy',
            code: '''project.read {
    def target = mops.lookup.requireNode('NODE_REF')
    def found = []
    mops.search.eachUsageOf(target, project.scope) { reference ->
        if (reference.link.name == 'classifier') found << reference.sourceNode
    }
    found
}''',
            bindings: ['NODE_REF': 'old'],
            verify: 'assert result == [fixture.type]',
        ],
        'usage-roots': [
            kind: 'groovy',
            code: '''project.read {
    def target = mops.lookup.requireNode('NODE_REF')
    def roots = []
    mops.search.eachUsageOf(target, project.scope) { reference ->
        roots << reference.sourceNode.containingRoot
    }
    roots.unique { it.reference }
}''',
            bindings: ['NODE_REF': 'old'],
            before: '''project.command {
    def root = mops.lookup.requireNode('CLASS_REF')
    mops.parsing.java.addJavaMembersFromString(root, 'private Example otherPeer;', null)
    def target = mops.lookup.requireNode('NODE_REF')
    def types = root.descendants.findAll {
        it.concept.qualifiedName.endsWith('.ClassifierType')
    }
    types.each { it.references['classifier'] = target }
    types.size()
}''',
            verify: '''assert before == '2'
assert result == [fixture.root]''',
        ],
        'name-pattern-help': [
            kind: 'cli',
            code: '''mops explain name-pattern''',
        ],
    ],
]
