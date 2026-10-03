# Insert Java as BaseLanguage nodes

The parser accepts Java 8 snippets and inserts native nodes. Return both `nodes` and `unresolved`; a successful parse
with no unresolved nodes still requires a full model check. Classifier snippets go into a model, member snippets into a
classifier, and statement snippets into a statement list. The optional third argument inserts before an existing child;
`null` appends.

<!-- markdownlint-disable MD013 -->

```groovy
{{parse-classes}}
```

<!-- markdownlint-enable MD013 -->

<!-- markdownlint-disable MD013 -->

```groovy
{{parse-members}}
```

<!-- markdownlint-enable MD013 -->

<!-- markdownlint-disable MD013 -->

```groovy
{{parse-statements}}
```

<!-- markdownlint-enable MD013 -->
