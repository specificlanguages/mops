# Insert Java as BaseLanguage nodes

The parser accepts Java 8 snippets and inserts native nodes. Return both `nodes` and `unresolved`. Method and
constructor calls are rebound to the overload their argument types select when MPS can decide it; a call it cannot
decide keeps the target found by name. A successful parse with no unresolved nodes still requires a full model check.
Classifier snippets go into a model, member snippets into a classifier, and statement snippets into a statement list.
The optional third argument inserts before an existing child; `null` appends.

```groovy
{{parse-classes}}
```

```groovy
{{parse-members}}
```

```groovy
{{parse-statements}}
```
