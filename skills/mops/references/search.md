# Search

Default searches cover editable project sources. An explicit `in` scope can include library/platform content.
`find instances` includes subconcepts by default; `--exact` restricts it to the direct concept.

| Task                                            | Command                                                                                                                |
| ----------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| Find class nodes                                | `mops find instances jetbrains.mps.baseLanguage.ClassConcept`                                                          |
| Find only direct instances                      | `mops find instances --exact jetbrains.mps.baseLanguage.ClassConcept`                                                  |
| Find named roots by camel-hump/wildcard pattern | `mops find root-by-name '*Builder*'`                                                                                   |
| Search roots in one model                       | `mops find root-by-name '*Builder*' in sample.solution .main`                                                          |
| Search roots across the repository              | `mops find root-by-name '*Builder*' in /`                                                                              |
| Find references to a node                       | `mops find usages 'NODE_REF'`                                                                                          |
| Find all matching node IDs                      | `mops find node-by-id 123456789 in /`                                                                                  |
| Get machine-readable matches                    | `mops find instances --json jetbrains.mps.baseLanguage.ClassConcept`                                                   |
| Return every match as a reference               | `mops find root-by-name --refs-only --limit 0 '*Builder*'`                                                             |
| Render every matching root                      | `mops find root-by-name --refs-only --limit 0 '*Builder*' \| while IFS= read -r ref; do mops render node "$ref"; done` |

`find` defaults to 100 matches; `--limit 0` removes the cap. `--refs-only` cannot be combined with `--json`. Use
`mops explain name-pattern` for pattern matching rules.

## Code Mode: concepts and instances

Save a program below as `search.groovy` and run `mops code run search.groovy`. Concept lookups and instance searches
belong in `project.read`.

Look up a concept by fully qualified name:

```groovy
project.read {
    def concept = mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.structure.ClassConcept')
    concept.qualifiedName
}
```

Return null when a concept name is missing:

```groovy
project.read {
    mops.lookup.conceptByName('jetbrains.mps.baseLanguage.structure.NoSuchMopsConcept') == null
}
```

`requireConceptByName` throws on a missing concept; `conceptByName` returns null. Both reject ambiguous names and
untrusted language runtimes. Fully qualified names include the structure model, such as
`jetbrains.mps.baseLanguage.structure.ClassConcept`.

Get a serialized concept ID:

```groovy
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
project.read {
    def concept = mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.structure.ClassConcept')
    PersistenceFacade.instance.asString(concept)
}
```

Look up a concept by serialized ID:

```groovy
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
project.read {
    def concept = PersistenceFacade.instance.createConcept(
        'c:f3061a53-9226-4cc5-a443-f952ceaf5816/1068390468198:jetbrains.mps.baseLanguage.structure.ClassConcept')
    [name: concept.qualifiedName, valid: concept.valid]
}
```

For ID lookup, copy the entire serialized value returned by `PersistenceFacade.instance.asString(concept)`, including
its prefix, language UUID, numeric concept ID, and name. `createConcept` reconstructs the concept descriptor;
check `concept.valid` before using it. A concept ID is distinct from a declaration node reference or a bare node ID.

Collect instances, including subconcepts, with custom result data:

```groovy
project.read {
    def concept = mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.structure.Classifier')
    def found = []
    mops.search.eachInstanceOf(concept, project.scope) { node ->
        found << [name: node.properties['name'], node: node]
    }
    found
}
```

Collect only direct instances:

```groovy
project.read {
    def concept = mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.structure.Classifier')
    def found = []
    mops.search.eachInstanceOf(concept, project.scope, true) { node -> found << node }
    found
}
```

Find instances of a concept identified by ID:

```groovy
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
project.read {
    def concept = PersistenceFacade.instance.createConcept(
        'c:f3061a53-9226-4cc5-a443-f952ceaf5816/1068390468198:jetbrains.mps.baseLanguage.structure.ClassConcept')
    assert concept.valid
    def found = []
    mops.search.eachInstanceOf(concept, project.scope) { node -> found << node }
    found
}
```

`mops.search.eachInstanceOf` passes native `SNode` objects to the closure. It includes subconcepts unless the third
argument is `true`. The example searches for `Classifier`, so the subclass `ClassConcept` matches the first search
and is excluded by the direct-instance search. `project.scope` covers the project's modules, including generators.
These searches have no CLI result limit; collect the data you need in the closure and return it. Native nodes in
the returned lists or maps become serialized node references.

## Code Mode: usages

Replace `NODE_REF` with the serialized reference of the target node. Run these programs with
`mops code run search.groovy`; usage searches belong in `project.read`.

Collect incoming references with their roles and source nodes:

```groovy
project.read {
    def target = mops.lookup.requireNode('NODE_REF')
    def found = []
    mops.search.eachUsageOf(target, project.scope) { reference ->
        found << [role: reference.link.name, source: reference.sourceNode]
    }
    found
}
```

Collect only source nodes whose reference uses the `classifier` role:

```groovy
project.read {
    def target = mops.lookup.requireNode('NODE_REF')
    def found = []
    mops.search.eachUsageOf(target, project.scope) { reference ->
        if (reference.link.name == 'classifier') found << reference.sourceNode
    }
    found
}
```

Collect the roots containing usages, returning each root once even if it contains multiple references:

```groovy
project.read {
    def target = mops.lookup.requireNode('NODE_REF')
    def roots = []
    mops.search.eachUsageOf(target, project.scope) { reference ->
        roots << reference.sourceNode.containingRoot
    }
    roots.unique { it.reference }
}
```

`mops.search.eachUsageOf` passes native `SReference` objects to the closure. `reference.sourceNode` is the node
holding the reference; `reference.link.name` is its role. `project.scope` covers the project's modules, including
generators. Results have no CLI limit. Return source nodes or containing roots to get serialized node references;
collect role names and other metadata in maps when needed.
