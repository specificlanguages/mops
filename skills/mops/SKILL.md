---
name: mops
description:
  Inspect, create, edit, build, check, and test JetBrains MPS projects with the mops CLI and Groovy Code Mode. Use when
  working with MPS models through mops, choosing commands, or discovering its APIs and task recipes.
---

# Working with MPS through mops

## Choose commands and find targets

Use the project’s `mopsw` wrapper when available. Start with `mops examples` for task recipes and `mops --help` for
commands. Use `list`, `find`, `get node`, and `render node` to inspect models and obtain stable references before
editing. Copy serialized references whole; names can be ambiguous. Prefer a direct CLI command when it handles the task.

Read `mops examples editing` to choose JSON edit batches, Java insertion, or Code Mode. JSON batches report containment
constraint violations; Code Mode supports custom traversal and logic. Check edited models with `check model`, build with
`make module`, and run relevant tests with `test`. Constraint checks alone do not establish model or build correctness.

For concurrent agents, daemon restarts, or stuck processes, read the skill printed by `mops skill path daemon`.

## Code Mode

Run Groovy against the project's MPS daemon with `mops code run program.groovy`. Omit the file or use `-` to read stdin.
Select the project with `--project-root PATH`, or run from inside its directory. Use the project's `mopsw` wrapper when
available; it supplies the MPS and Java homes. Otherwise provide `--mps-home PATH` and `--java-home PATH` as needed.

Use the live API help before calling an unfamiliar operation:

```sh
mops code help
mops code help mops.lookup
mops code help SNode.properties
mops code help Project.command --json
```

`help(object)` inside a program describes its runtime type. `project` is the native MPS project; `mops` provides lookup,
search, parsing, and editing helpers. Classes on the daemon/MPS classpath are available; Groovy `@Grab` dependencies are
disabled. Each invocation gets a fresh binding, so return references or data needed by a later invocation.

Run Code Mode programs and other exclusive operations sequentially for the same project. `code run` remains exclusive
even when its program only reads. Independent CLI lookups, navigation, searches, diagnostics, and `code help` can run
concurrently. Use `mops skill path daemon` for coordination and recovery guidance.

## Model access and results

Put lookup and model reads inside `project.read { ... }`, and mutations inside `project.command { ... }`. Do not nest
these access blocks. A command saves after successful completion, but is non-transactional: an exception does not roll
back mutations. Resolve and validate targets before changing them.

Use `mops.lookup.requireModel`, `requireNode`, and `requireConceptByName` to fail clearly when a target is missing.
Model names must resolve uniquely; serialized references also work for model and node lookup. Properties use
`node.properties['name']`; children use `node.child['role']` or `node.children['role']`; references use
`node.references['role']`, whose `targetNode` resolves the target. These accessors require model access and their
assignments require command access. Children and references shadow Groovy's native getter properties; consult
`code help` for native iteration alternatives.

Return the final expression instead of relying on `println`. Strings print as text; MPS nodes, models, and modules
become serialized references. Maps and collections become JSON, including references for contained MPS objects. Convert
other JVM objects to supported data while still inside the access block.

Run rendering, make, and test operations outside access blocks, as specified by `code help`. Check their returned
diagnostics and outcomes; a returned failure report does not itself make `code run` exit nonzero.
`code run --timeout SECONDS` sets the hard deadline (default 900; 0 disables it); expiry terminates the daemon.

## Task recipes

Start with `mops examples` for the topic index, or read [the bundled index](references/README.md).
`mops examples editing` helps choose creation, node editing, references/imports, Java insertion, or JSON batches. Read
only the topic relevant to the task; `mops examples all` prints the full collection.

## Examples

Replace `baselanguage.sandbox` with the target model name.

### List roots

Return names and references together so subsequent operations can use stable targets.

```groovy
project.read {
    def model = mops.lookup.requireModel('baselanguage.sandbox')
    model.rootNodes.collect { node ->
        [name: node.properties['name'], concept: node.concept.qualifiedName, node: node]
    }
}
```

### Find classes

Search editable project sources and include subconcepts by default.

```groovy
project.read {
    def concept = mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.structure.ClassConcept')
    def found = []
    mops.search.eachInstanceOf(concept, project.scope) { node ->
        found << [name: node.properties['name'], node: node]
    }
    found
}
```

### Add a class

Create a detached native node, set its name, then attach it as a model root. This example adds a new class each time it
runs.

```groovy
project.command {
    def model = mops.lookup.requireModel('baselanguage.sandbox')
    def concept = mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.structure.ClassConcept')
    def node = model.createNode(concept)
    node.properties['name'] = 'SkillExample'
    model.addRootNode(node)
    [name: node.properties['name'], node: node]
}
```
