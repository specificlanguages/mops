def conceptName = 'jetbrains.mps.baseLanguage.ClassConcept'

return project.read {
    def concept = project.concept(conceptName)
    def names = []
    eachInstanceOf(concept, project.scope) { node ->
        names << node.properties['name']
    }
    return names
}
