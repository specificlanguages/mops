[
    groups: [
        'classes': [
            [
                title: 'Load a class from a module\'s class loader',
                kind: 'groovy',
                code: '''project.read { mops.runtime.requireClass('jetbrains.mps.baseLanguage', 'jetbrains.mps.baseLanguage.behavior.ClassConcept__BehaviorDescriptor').name }''',
                verify: '''
                    assert result == 'jetbrains.mps.baseLanguage.behavior.ClassConcept__BehaviorDescriptor'
                ''',
                output: 'text',
            ],
            [
                title: 'Detect a missing class without an exception',
                kind: 'groovy',
                code: '''project.read { mops.runtime.loadClass('jetbrains.mps.baseLanguage', 'jetbrains.mps.baseLanguage.behavior.NoSuchClass') == null }''',
                verify: '''
                    assert result == 'true'
                ''',
                output: 'text',
            ],
            [
                title: 'Call a static method that takes a node',
                kind: 'groovy',
                code: '''project.read { def operations = mops.runtime.requireClass('jetbrains.mps.lang.smodel', 'jetbrains.mps.lang.smodel.generator.smodelAdapter.SNodeOperations'); operations.getMethod('getConcept', org.jetbrains.mps.openapi.model.SNode).invoke(null, mops.lookup.requireNode('NODE_REF')).qualifiedName }''',
                verify: '''
                    assert result == 'jetbrains.mps.baseLanguage.structure.ClassConcept'
                ''',
                output: 'text',
            ],
        ],
        'commands': [
            [
                title: 'Compile a project module before loading its classes',
                kind: 'cli',
                code: '''mops make module sample.language''',
            ],
            [
                title: 'Inspect the runtime helpers',
                kind: 'cli',
                code: '''mops code help mops.runtime''',
            ],
            [
                title: 'Explain why a module\'s runtime is not loaded',
                kind: 'cli',
                code: '''mops diagnose module sample.language''',
            ],
        ],
    ],
    snippets: [
        'singleton': [
            kind: 'groovy',
            code: '''project.read {
    def registry = mops.runtime.requireClass('sample.language', 'sample.language.runtime.Registry')
    def instance = registry.getMethod('getInstance').invoke(null)
    instance.entries.collect { it.toString() }
}''',
            untested: 'Requires a compiled language with a runtime solution.',
        ],
        'behavior': [
            kind: 'groovy',
            code: '''import org.jetbrains.mps.openapi.model.SNode
project.read {
    def node = mops.lookup.requireNode('NODE_REF')
    def evaluator = mops.runtime.requireClass('sample.language', 'sample.language.runtime.Evaluator')
    evaluator.getMethod('evaluate', SNode).invoke(evaluator.getDeclaredConstructor().newInstance(), node).toString()
}''',
            untested: 'Requires a compiled language with a runtime solution.',
        ],
        'plugin': [
            kind: 'groovy',
            code: '''import org.jetbrains.mps.openapi.model.SModel
project.read {
    def model = mops.lookup.requireModel('sample.model')
    def statistics = mops.runtime.requireClass('sample.plugin', 'sample.plugin.util.ModelStatistics')
    statistics.getMethod('count', SModel).invoke(null, model)
}''',
            untested: 'Requires a compiled plugin solution.',
        ],
    ],
]
