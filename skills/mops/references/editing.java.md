# Insert Java as BaseLanguage nodes

The parser accepts Java 8 snippets and inserts native nodes. Return both `nodes` and `unresolved`; a successful parse
with no unresolved nodes still requires a full model check. Classifier snippets go into a model, member snippets into a
classifier, and statement snippets into a statement list. The optional third argument inserts before an existing child;
`null` appends.

<!-- markdownlint-disable MD013 -->

```groovy
project.command { def r = mops.parsing.java.addJavaClassesFromString(mops.lookup.requireModel('sample.model'), 'public class Example { public static int answer() { return 42; } }'); [nodes: r.nodes, unresolved: r.unresolved] }
```

<!-- markdownlint-enable MD013 -->

<!-- markdownlint-disable MD013 -->

```groovy
project.command { def r = mops.parsing.java.addJavaMembersFromString(mops.lookup.requireNode('CLASS_REF'), 'private int count; public int size() { return count; }', null); [nodes: r.nodes, unresolved: r.unresolved] }
```

<!-- markdownlint-enable MD013 -->

<!-- markdownlint-disable MD013 -->

```groovy
project.command { def r = mops.parsing.java.addJavaStatementsFromString(mops.lookup.requireNode('STATEMENTS_REF'), 'return 42;', null); [nodes: r.nodes, unresolved: r.unresolved] }
```

<!-- markdownlint-enable MD013 -->
