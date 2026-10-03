import groovy.json.JsonOutput

// Inputs and outputs are supplied by the Gradle task.
outputDir.deleteDir()
def cases = []
def shell = new GroovyShell()
new File(sourceDir, 'pages').listFiles().sort { it.name }.each { template ->
    def topic = template.name - '.md'
    def specs = shell.evaluate(new File(sourceDir, "specs/${topic}.groovy"))
    assert specs instanceof Map: "${topic}: expected an example map"
    def page = template.getText('UTF-8')
    specs.each { id, spec ->
        assert spec.kind in ['cli', 'shell', 'groovy']: "${topic}/${id}: unknown kind"
        assert spec.code instanceof String && spec.code.trim(): "${topic}/${id}: missing code"
        assert page.contains("{{${id}}}"): "${topic}/${id}: example has no documentation slot"
        if (spec.kind == 'groovy') {
            assert (spec.verify?.trim() as boolean) != (spec.untested?.trim() as boolean):
                "${topic}/${id}: declare verify or an explicit untested reason"
        }
        spec.code = spec.code.trim()
        page = page.replace("{{${id}}}", spec.code)
        cases << ([topic: topic, id: id] + spec)
    }
    assert !(page =~ /\{\{[^}]+\}\}/): "${topic}: unresolved example slot"
    // Keep generated tables in the same padded format as the Markdown formatter.
    def lines = page.readLines()
    for (int start = 0; start < lines.size(); start++) {
        if (!lines[start].startsWith('|')) continue
        int end = start
        while (end < lines.size() && lines[end].startsWith('|')) end++
        def rows = lines.subList(start, end).collect { line ->
            line.substring(1, line.length() - 1).split(/(?<!\\)\|/).collect { it.trim() }
        }
        def widths = rows[0].indices.collect { column ->
            rows.withIndex().findAll { row, index -> index != 1 }.collect { row, index -> row[column].size() }.max()
        }
        rows.eachWithIndex { row, index ->
            lines[start + index] = '| ' + row.withIndex().collect { cell, column ->
                index == 1 ? '-' * widths[column] : cell.padRight(widths[column])
            }.join(' | ') + ' |'
        }
        start = end - 1
    }
    page = lines.join('\n') + '\n'
    def output = new File(outputDir, "pages/${template.name}")
    output.parentFile.mkdirs()
    output.setText('<!-- Generated from examples/pages and examples/specs; run :cli:updateExamples. -->\n\n' + page, 'UTF-8')
}
def manifest = new File(outputDir, 'specs/examples.json')
manifest.parentFile.mkdirs()
manifest.setText(JsonOutput.prettyPrint(JsonOutput.toJson(cases)) + '\n', 'UTF-8')
