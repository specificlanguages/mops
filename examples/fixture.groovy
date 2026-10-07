project.command {
    def module = mops.lookup.requireModel('baselanguage.sandbox').module
    def model = module.createModel('CASE_MODEL_NAME')
    def root = mops.parsing.java.addJavaClassesFromString(model,
        'public class Example { private Example peer; public int answer() { return 1; } }').nodes[0]
    def method = root.children['member'].find { it.properties['name'] == 'answer' }
    def type = root.descendants.find { it.concept.qualifiedName.endsWith('.ClassifierType') }
    def oldModel = module.createModel('CASE_MODEL_NAME.old')
    def newModel = module.createModel('CASE_MODEL_NAME.new')
    def old = mops.parsing.java.addJavaClassesFromString(oldModel, 'public class OldTarget {}').nodes[0]
    def replacement = mops.parsing.java.addJavaClassesFromString(newModel, 'public class NewTarget {}').nodes[0]
    type.references['classifier'] = old
    new jetbrains.mps.smodel.ModelImports(model).addModelImport(oldModel.reference)
    model.save()
    [model: model, oldModel: oldModel, newModel: newModel, newModelName: 'CASE_MODEL_NAME.new', oldModelName: 'CASE_MODEL_NAME.old', root: root, method: method, type: type, statements: method.child['body'],
        members: root.children['member'], old: old, replacement: replacement]
}
