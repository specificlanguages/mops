# Search

Default searches cover editable project sources and skip models MPS marks read-only, even when their files are local.
An explicit `in` scope can include library/platform content; `in /` searches the whole repository. `find instances`
includes subconcepts by default; `--exact` restricts it to the direct concept. `find usages` follows exact reference
targets, so searching for a class root does not find references to its constructors. `find node-by-id` turns a node ID
from a `.mps` file into full references; those files spell default numeric IDs in an encoded base64 form, while mops
prints decimal IDs, and the command accepts both.

{{table:commands|Command}}

`find` defaults to 100 matches; `--limit 0` removes the cap. `--refs-only` cannot be combined with `--json`. Use
`{{name-pattern-help}}` for pattern matching rules.

## Code Mode: concepts and instances

Save a program below as `search.groovy` and run `mops code run search.groovy`. Concept lookups and instance searches
belong in `project.read`.

Look up a concept by fully qualified name:

```groovy
{{concept-by-name}}
```

Return null when a concept name is missing:

```groovy
{{missing-concept}}
```

`requireConceptByName` throws on a missing concept; `conceptByName` returns null. Both reject ambiguous names and
untrusted language runtimes. Both accept the same spellings as `find instances`: the qualified name
(`jetbrains.mps.baseLanguage.structure.ClassConcept`), the same name with `.structure.` dropped
(`jetbrains.mps.baseLanguage.ClassConcept`), or a bare short name that is unique across loaded languages.

Get a serialized concept ID:

```groovy
{{serialize-concept}}
```

Look up a concept by serialized ID:

```groovy
{{concept-by-id}}
```

For ID lookup, copy the entire serialized value returned by `PersistenceFacade.instance.asString(concept)`, including
its prefix, language UUID, numeric concept ID, and name. `createConcept` reconstructs the concept descriptor;
check `concept.valid` before using it. A concept ID is distinct from a declaration node reference or a bare node ID.

Collect instances, including subconcepts, with custom result data:

```groovy
{{instances}}
```

Collect only direct instances:

```groovy
{{exact-instances}}
```

Find instances of a concept identified by ID:

```groovy
{{instances-by-id}}
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
{{usages}}
```

Collect only source nodes whose reference uses the `classifier` role:

```groovy
{{usages-by-role}}
```

Collect the roots containing usages, returning each root once even if it contains multiple references:

```groovy
{{usage-roots}}
```

`mops.search.eachUsageOf` passes native `SReference` objects to the closure. `reference.sourceNode` is the node
holding the reference; `reference.link.name` is its role. `project.scope` covers the project's modules, including
generators. Results have no CLI limit. Return source nodes or containing roots to get serialized node references;
collect role names and other metadata in maps when needed.
