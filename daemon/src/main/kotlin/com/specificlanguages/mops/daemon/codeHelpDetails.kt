package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.*

internal fun options(path: String): List<CodeHelpOption> {
    val descriptor = CodeHelpOption("descriptor", "String?", "null", "Descriptor path; null uses the module creator's default location")
    return when (path) {
        "Project.createLanguage" -> listOf(descriptor, CodeHelpOption("withGenerator", "boolean", "false", "Create a default generator with the language"))
        "Project.createSolution" -> listOf(descriptor, CodeHelpOption("usagePreset", "String", "NOT_GENERATED", "Case-insensitive; hyphens are normalized to underscores", SolutionUsagePreset.entries.map { it.name }))
        "Project.createDevkit" -> listOf(descriptor)
        "Language.createGenerator" -> listOf(descriptor, CodeHelpOption("standalone", "boolean", "false", "Persist the generator as a standalone module"))
        "mops.testing.run" -> listOf(CodeHelpOption("build", "boolean", "true", "Build before running tests"), CodeHelpOption("timeout", "integer", "900", "Timeout in seconds; 0 disables; nonnegative"))
        else -> emptyList()
    }
}

internal fun examples(path: String): List<String> = listOf(withReturn(when (path) {
    "Project.read" -> "project.read { project.projectModules.collect { it.moduleName } }"
    "Project.command" -> "project.command { project.createSolution('sample.solution').moduleName }"
    "Project.make" -> "return project.make()"
    "Project.createLanguage" -> "project.command { project.createLanguage('sample.language', [withGenerator: true]).moduleName }"
    "Project.createSolution" -> "project.command { project.createSolution('sample.solution', [usagePreset: 'JAVA']).moduleName }"
    "Project.createDevkit" -> "project.command { project.createDevkit('sample.devkit', [:]).moduleName }"
    "Language.createGenerator" -> "project.command {\n  def language = mops.lookup.requireModule('sample.language')\n  language.createGenerator('main', [standalone: true]).moduleName\n}"
    "SModule.createModel" -> "project.command { mops.lookup.requireModule('sample.solution').createModel('sample.model', false).name.toString() }"
    "SNode.render" -> "def node = project.read { mops.lookup.requireNode('NODE_REFERENCE') }\nnode.render()"
    "mops.testing.run" -> "def report = mops.testing.run(project, [build: true, timeout: 900])\n[successful: report.successful, diagnostics: report.diagnostics]"
    "mops.editing.build" -> "help(mops.editing.build)"
    "mops.editing.build.reloadModulesFromDisk" -> "project.command {\n  def result = mops.editing.build.reloadModulesFromDisk(mops.lookup.requireNode('BUILD_PROJECT_NODE_REFERENCE'))\n  [succeeded: result.succeeded, messages: result.messages.collect { [kind: it.kind, text: it.text, node: it.node] }]\n}"
    "mops.parsing.java" -> "help(mops.parsing.java)"
    "mops.parsing.java.addJavaClassesFromString" -> "project.command {\n  def result = mops.parsing.java.addJavaClassesFromString(mops.lookup.requireModel('sample.model'), 'class Example {}')\n  [nodes: result.nodes, unresolved: result.unresolved]\n}"
    "mops.parsing.java.addJavaMembersFromString" -> "project.command {\n  def classifier = mops.lookup.requireNode('CLASSIFIER_NODE_REFERENCE')\n  def result = mops.parsing.java.addJavaMembersFromString(classifier, 'void example() {}', null)\n  [nodes: result.nodes, unresolved: result.unresolved]\n}"
    "mops.parsing.java.addJavaStatementsFromString" -> "project.command {\n  def statements = mops.lookup.requireNode('STATEMENT_LIST_NODE_REFERENCE')\n  def result = mops.parsing.java.addJavaStatementsFromString(statements, 'return;', null)\n  [nodes: result.nodes, unresolved: result.unresolved]\n}"
    "mops.search.eachUsageOf" -> "import jetbrains.mps.project.GlobalScope\nproject.read {\n  def usages = []\n  def scope = new GlobalScope(project.repository)\n  mops.search.eachUsageOf(mops.lookup.requireNode('NODE_REFERENCE'), scope) { reference -> usages << reference.sourceNode }\n  usages\n}"
    "mops.search.eachInstanceOf" -> "import jetbrains.mps.project.GlobalScope\nproject.read {\n  def nodes = []\n  def scope = new GlobalScope(project.repository)\n  mops.search.eachInstanceOf(mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.structure.ClassConcept'), scope, false) { node -> nodes << node }\n  nodes\n}"
    "global.help" -> "help('project.createSolution')"
    "SNode.properties" -> "project.read { mops.lookup.requireNode('NODE_REFERENCE').properties['name'] }"
    "SNode.child" -> "project.read { mops.lookup.requireNode('NODE_REFERENCE').child['body'] }"
    "SNode.children" -> "project.read { mops.lookup.requireNode('NODE_REFERENCE').children['member'] }"
    "SNode.references" -> "project.read { mops.lookup.requireNode('NODE_REFERENCE').references['type']?.targetNode }"
    else -> {
        val operation = path.substringAfterLast('.')
        val argument = when {
            operation.contains("Concept") || operation == "conceptByName" -> "jetbrains.mps.baseLanguage.structure.ClassConcept"
            operation.contains("Node") || operation == "node" -> "NODE_REFERENCE"
            operation.contains("Module") || operation == "module" -> "sample.solution"
            else -> "sample.model"
        }
        "project.read { $path('$argument')${if (operation.contains("Concept") || operation == "conceptByName") "?.qualifiedName" else ""} }"
    }
}))

private fun member(type: String, name: String, valueType: String, summary: String) = CodeHelpEntry(
    "$type.$name", "$type.$name: $valueType", summary, "no access block required [none]", returnType = valueType.removeSuffix("?"), nullable = valueType.endsWith("?"), related = listOf(valueType.removeSuffix("?").removePrefix("List<").removeSuffix(">")).filter { it !in listOf("String", "boolean", "int") },
)

internal val resultMembers = mapOf(
    "JavaParsingResult" to listOf(
        member("JavaParsingResult", "nodes", "List<SNode>", "Inserted native nodes. Return a map of nodes/unresolved to serialize references at the CLI."),
        member("JavaParsingResult", "unresolved", "List<SNode>", "Native nodes left unresolved after parsing; this does not replace a full model check."),
    ),
    "BuildModuleReloadResult" to listOf(
        member("BuildModuleReloadResult", "succeeded", "boolean", "Whether reloading succeeded; successful partial updates are retained."),
        member("BuildModuleReloadResult", "messages", "List<BuildModuleReloadMessage>", "Ordered warnings and errors."),
    ),
    "TestRunReport" to listOf(
        member("TestRunReport", "successful", "boolean", "True when complete and outcome is SUCCESS."),
        member("TestRunReport", "complete", "boolean", "Whether the run completed."),
        member("TestRunReport", "results", "List<TestResult>", "Individual test outcomes."),
        member("TestRunReport", "diagnostics", "List<String>", "Worker and run diagnostics."),
        member("TestRunReport", "reportPath", "String", "Path to the saved report."),
        member("TestRunReport", "outcome", "String", "Overall run outcome."),
        member("TestRunReport", "phase", "String", "Last run phase."),
        member("TestRunReport", "discovered", "int", "Number of discovered tests."),
        member("TestRunReport", "build", "MakeResponse?", "Build result if building was requested."),
    ),
    "BuildModuleReloadMessage" to listOf(
        member("BuildModuleReloadMessage", "kind", "String", "Warning or error severity."),
        member("BuildModuleReloadMessage", "text", "String", "Diagnostic text."),
        member("BuildModuleReloadMessage", "node", "SNode?", "Associated native node, if available."),
    ),
    "TestResult" to listOf("id", "name", "kind", "status", "source", "className", "methodName", "detail").map { name ->
        member("TestResult", name, if (name in listOf("source", "className", "methodName", "detail")) "String?" else "String", "Test result $name.")
    },
    "MakeMessageJson" to listOf(
        member("MakeMessageJson", "kind", "MakeMessageKind", "Warning or error severity."),
        member("MakeMessageJson", "text", "String", "Make diagnostic text."),
    ),
    "MakeResponse" to listOf(
        member("MakeResponse", "outcome", "MakeOutcome", "Overall make outcome; the result can be returned directly to the CLI."),
        member("MakeResponse", "moduleCount", "int", "Number of modules made."),
        member("MakeResponse", "messages", "List<MakeMessageJson>", "Make warnings and errors."),
    ),
)

private fun withReturn(example: String): String = when {
    example.startsWith("return ") -> example
    example.startsWith("import ") -> example.replace("\nproject.read", "\nreturn project.read")
    example.startsWith("def report") -> example.replace("\n[successful:", "\nreturn [successful:")
    example.startsWith("def node") -> example.replace("\nnode.render()", "\nreturn node.render()")
    else -> "return $example"
}
