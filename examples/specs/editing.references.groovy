[
    snippets: [
        'retarget-usages': [
            kind: 'groovy',
            code: '''project.command { def old = mops.lookup.requireNode('OLD_REF'); def replacement = mops.lookup.requireNode('NEW_REF'); def usages = []; mops.search.eachUsageOf(old, project.scope) { usages << it }; usages.each { it.sourceNode.references[it.link.name] = replacement }; usages.size() }''',
            verify: '''
                assert result == 1
                assert run("project.read { mops.lookup.requireNode('TYPE_REF').references['classifier'].targetNode }") == fixture.replacement
            ''',
        ],
        'replace-import': [
            kind: 'groovy',
            before: '''project.command { mops.lookup.requireNode('TYPE_REF').references['classifier'] = mops.lookup.requireNode('TARGET_REF') }''',
            code: '''project.command { def model = mops.lookup.requireModel('sample.model'); def old = mops.lookup.requireModel('old.model'); def replacement = mops.lookup.requireModel('new.model'); def imports = new jetbrains.mps.smodel.ModelImports(model); imports.removeModelImport(old.reference); imports.addModelImport(replacement.reference); model.save(); model }''',
            verify: '''
                assert result == fixture.model
                assert modelFile.getText('UTF-8').contains(fixture.newModelName)
                assert !modelFile.getText('UTF-8').contains('(' + fixture.oldModelName + ')')
            ''',
            output: 'text',
        ],
    ],
]
