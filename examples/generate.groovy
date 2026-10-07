import groovy.json.JsonOutput

// Inputs and outputs are supplied by the Gradle task.
outputDir.deleteDir()
def cases = []
def shell = new GroovyShell()
new File(sourceDir, 'pages').listFiles().sort { it.name }.each { template ->
    def topic = template.name - '.md'
    def catalog = shell.evaluate(new File(sourceDir, "specs/${topic}.groovy"))
    assert catalog instanceof Map: "${topic}: expected a catalog map"
    def groups = catalog.groups ?: [:]
    def snippets = catalog.snippets ?: [:]
    def validate = { spec, name ->
        assert spec.kind in ['cli', 'shell', 'groovy']: "${name}: unknown kind"
        assert spec.code instanceof String && spec.code.trim(): "${name}: missing code"
        if (spec.kind == 'groovy') {
            assert (spec.verify?.trim() as boolean) != (spec.untested?.trim() as boolean):
                "${name}: declare verify or an explicit untested reason"
        }
        spec.code = spec.code.trim()
    }
    groups.each { group, members ->
        assert members instanceof List: "${topic}/${group}: expected a list of examples"
        members.each { spec ->
            assert spec.title instanceof String && spec.title.trim(): "${topic}/${group}: example needs a title"
            validate(spec, "${topic}/${group}/${spec.title}")
            cases << ([topic: topic, group: group] + spec)
        }
    }
    snippets.each { id, spec ->
        validate(spec, "${topic}/${id}")
        cases << ([topic: topic, id: id] + spec)
    }
    def usedGroups = [] as Set
    def usedSnippets = [] as Set
    def page = template.getText('UTF-8').replaceAll(/\{\{table:([^|}]+)\|([^}]+)\}\}/) { match, group, heading ->
        def members = groups[group]
        assert members: "${topic}: unknown or empty table group '${group}'"
        usedGroups << group
        def rows = [['Task', heading]] + members.collect { spec ->
            [spec.title, "`${spec.code.replace('|', '\\|')}`"]
        }
        def widths = [0, 1].collect { column -> rows.collect { it[column].size() }.max() }
        def render = { row -> '| ' + [0, 1].collect { column -> row[column].padRight(widths[column]) }.join(' | ') + ' |' }
        ([render(rows.first()), '| ' + widths.collect { '-' * it }.join(' | ') + ' |'] + rows.drop(1).collect(render)).join('\n')
    }
    page = page.replaceAll(/\{\{([^}]+)\}\}/) { match, id ->
        assert snippets.containsKey(id): "${topic}: unknown snippet '${id}'"
        usedSnippets << id
        snippets[id].code
    }
    assert usedGroups == groups.keySet(): "${topic}: undocumented groups ${groups.keySet() - usedGroups}"
    assert usedSnippets == snippets.keySet(): "${topic}: undocumented snippets ${snippets.keySet() - usedSnippets}"
    def output = new File(outputDir, "pages/${template.name}")
    output.parentFile.mkdirs()
    output.setText(page, 'UTF-8')
}
def manifest = new File(outputDir, 'specs/examples.json')
manifest.parentFile.mkdirs()
manifest.setText(JsonOutput.prettyPrint(JsonOutput.toJson(cases)) + '\n', 'UTF-8')
