[
    'parse-classes': [
        kind: 'groovy',
        code: '''project.command { def r = mops.parsing.java.addJavaClassesFromString(mops.lookup.requireModel('sample.model'), 'public class Example { public static int answer() { return 42; } }'); [nodes: r.nodes, unresolved: r.unresolved] }''',
        verify: '''
            assert result.nodes.size() == 1
            assert result.unresolved == []
            assert run("project.read { mops.lookup.requireNode('${result.nodes[0]}').properties['name'] }") == 'Example'
        ''',
    ],
    'parse-members': [
        kind: 'groovy',
        code: '''project.command { def r = mops.parsing.java.addJavaMembersFromString(mops.lookup.requireNode('CLASS_REF'), 'private int count; public int size() { return count; }', null); [nodes: r.nodes, unresolved: r.unresolved] }''',
        verify: '''
            assert result.nodes.size() == 2
            assert result.unresolved == []
            assert run("project.read { mops.lookup.requireNode('${result.nodes[1]}').properties['name'] }") == 'size'
        ''',
    ],
    'parse-statements': [
        kind: 'groovy',
        code: '''project.command { def r = mops.parsing.java.addJavaStatementsFromString(mops.lookup.requireNode('STATEMENTS_REF'), 'return 42;', null); [nodes: r.nodes, unresolved: r.unresolved] }''',
        verify: '''
            assert result.nodes.size() == 1
            assert result.unresolved == []
            assert run("project.read { mops.lookup.requireNode('${result.nodes[0]}').concept.qualifiedName }").endsWith('.ReturnStatement')
        ''',
    ],
]
