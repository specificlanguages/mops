import groovy.json.JsonOutput

// Inputs and outputs are supplied by the Gradle task.
outputDir.deleteDir()
def cases = []
def shell = new GroovyShell()
new File(sourceDir, 'pages').listFiles().sort { it.name }.each { template ->
    def topic = template.name - '.md'
    def specs = shell.evaluate(new File(sourceDir, "specs/${topic}.groovy"))
    assert specs instanceof Map: "${topic}: expected an example map"
    specs.each { id, spec ->
        assert spec.group instanceof String && spec.group.trim(): "${topic}/${id}: missing group"
        assert spec.kind in ['cli', 'shell', 'groovy']: "${topic}/${id}: unknown kind"
        assert spec.code instanceof String && spec.code.trim(): "${topic}/${id}: missing code"
        if (spec.kind == 'groovy') {
            assert (spec.verify?.trim() as boolean) != (spec.untested?.trim() as boolean):
                "${topic}/${id}: declare verify or an explicit untested reason"
        }
        spec.code = spec.code.trim()
        cases << ([topic: topic, id: id] + spec)
    }
    def used = [] as Set
    def page = template.getText('UTF-8').replaceAll(/\{\{table:([^|}]+)\|([^}]+)\}\}/) { match, group, heading ->
        def members = specs.findAll { id, spec -> spec.group == group }
        assert members: "${topic}: unknown or empty table group '${group}'"
        def rows = [['Task', heading]] + members.collect { id, spec ->
            assert spec.title instanceof String && spec.title.trim(): "${topic}/${id}: table example needs a title"
            used << id
            [spec.title, "`${spec.code.replace('|', '\\|')}`"]
        }
        def widths = [0, 1].collect { column -> rows.collect { it[column].size() }.max() }
        def render = { row -> '| ' + [0, 1].collect { column -> row[column].padRight(widths[column]) }.join(' | ') + ' |' }
        ([render(rows.first()), '| ' + widths.collect { '-' * it }.join(' | ') + ' |'] + rows.drop(1).collect(render)).join('\n')
    }
    page = page.replaceAll(/\{\{([^}]+)\}\}/) { match, id ->
        assert specs.containsKey(id): "${topic}: unknown example '${id}'"
        used << id
        specs[id].code
    }
    assert used == specs.keySet(): "${topic}: undocumented examples ${specs.keySet() - used}"
    def output = new File(outputDir, "pages/${template.name}")
    output.parentFile.mkdirs()
    output.setText(page, 'UTF-8')
}
def manifest = new File(outputDir, 'specs/examples.json')
manifest.parentFile.mkdirs()
manifest.setText(JsonOutput.prettyPrint(JsonOutput.toJson(cases)) + '\n', 'UTF-8')
