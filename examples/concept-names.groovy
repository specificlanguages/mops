def conceptName = 'jetbrains.mps.baseLanguage.ClassConcept'

return project.read {
    def concept = mops.lookup.requireConceptByName(conceptName)
    def names = []
    mops.search.eachInstanceOf(concept, project.scope) { node ->
        names << node.properties['name']
    }
    return names
}
