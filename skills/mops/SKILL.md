---
name: mops
description:
  Prepare, inspect, create, edit, build, check, and test JetBrains MPS projects with the mops CLI and Groovy Code Mode.
  Use when setting up mops for a project, working with MPS models through mops, choosing commands, or discovering its
  APIs and task recipes.
---

# Working with MPS through mops

## Prepare the project

Run `mops wrapper` in the project checkout. It detects the project's MPS and Java runtime from the Gradle build and
writes a `mopsw` wrapper script that passes both homes to mops. The command prints each wrapper's path on a `Wrapper:`
line. The wrapper goes into the Gradle project's build directory, so its location depends on the project layout:
MPS-extensions, for example, gets `code/extensions/build/mopsw` on current branches and `build/mopsw` on older ones.
Call the wrapper in place of `mops`; the examples in this skill write `mops`.

Writing the wrapper can download MPS and the Java runtime, but it builds nothing. Build the project with its own build
tool first; most mops operations need at least all project languages built.

Then run `mops diagnose project`. Its first line has the shape `modules <loaded>/<total> loaded <failed> failed`, and
the command exits 0 even when modules failed to load, so read the rows for the modules the task needs instead of relying
on the exit code. A build often leaves out sandbox or test languages; build them with `mops make module <module>...`,
and look into one failure with `mops diagnose module <module>`.

Instance and usage searches need every project language loaded. If mops does not work, or a language the task needs
cannot be loaded, stop and report the blocker together with what the build already showed. Search results from a partly
loaded project are incomplete.

## Choose commands and find targets

Use the project’s `mopsw` wrapper when available. Start with `mops examples` for task recipes and `mops --help` for
commands. Use `list`, `find`, `get node`, and `render node` to inspect models and obtain stable references before
editing. Copy serialized references whole; names can be ambiguous. Prefer a direct CLI command when it handles the task.

Pitfalls when navigating and searching:

- `list` shows at most 50 children per level and ends a cut level with a `# truncated: ...` line. Pass `--limit 0` when
  listing project modules or any node whose children may exceed that.
- A `.suffix` segment is only the part after the module name: `.main` after `sample.solution` means the model
  `sample.solution.main`. A bare `.` names the model named exactly like its module: `sample.app .`.
- `render node` shows the default editor's presentation, which can leave information out. Use `list --depth N` or
  `get node` for the subtree, and `get node --ancestry` for the containment chain up to the root, then render the nodes
  of interest.
- Default searches cover editable project sources and skip models MPS marks read-only, even when their files are local.
  When a result is missing, search an explicit model scope or `in /` for the whole repository.
- `find usages` follows exact reference targets: searching for a class root does not find references to its
  constructors.
- `find node-by-id` turns a node ID from a `.mps` file into full references. Those files spell default numeric IDs in an
  encoded base64 form, while mops prints decimal IDs; the command accepts both.

Read `mops examples editing` to choose JSON edit batches, Java insertion, or Code Mode. JSON batches report containment
constraint violations; Code Mode supports custom traversal and logic. Check edited models with `check model`, build with
`make module`, and run relevant tests with `test`. Constraint checks alone do not establish model or build correctness.

For test selection, build options, failure reports, and worker limitations, read `mops examples validation` or the
[bundled validation recipes](references/validation.md).

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

## Author and run native tests

Native MPS tests are model nodes, not Java source. Navigate and render an existing test in the project to learn its
shape, inspect the concept roles with `list --summary` and `get node`, and create the nodes with mops. Use Java parsing
for ordinary BaseLanguage code, then insert the test-language expressions and annotations as native nodes and repair
references. The Java parser does not understand test-language expressions and cannot resolve inherited framework calls
in a helper parsed on its own. Fixture choice and registration in the project's test build are MPS workflow questions;
follow the project's testing conventions.

Check the authored models, then run the selected test with `mops test --json 'TARGET'`; the runner builds by default.
Keep the test report and worker logs, and tell a runner startup or discovery failure apart from a behavioral failure.
When the run is evidence for a bug report, record the mops version (`mops --version`) together with the MPS and Java
versions and the preparation commands.

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
