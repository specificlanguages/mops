# mops command reference

Generated from the mops command line by `./gradlew :cli:updateReference`. Do not edit this page.

## Commands

- [mops find](find.md): Search editable MPS project sources or loaded concept hierarchies.
- [mops get](get.md): Get MPS project data.
- [mops render](render.md): Render MPS project data.
- [mops edit](edit.md): Edit MPS project data.
- [mops check](check.md): Run MPS checks.
- [mops make](make.md): Generate and compile modules or the whole project.
- [mops create](create.md): Create a project module or model.
- [mops diagnose](diagnose.md): Diagnose how the daemon loaded the MPS project.
- [mops daemon](daemon.md): Inspect or control mops daemon processes.
- [mops code](code.md): Run trusted Groovy against an open MPS project.
- [mops test](test.md): Build and run MPS tests in a separate process.
- [mops list](list.md): List an MPS navigation target as a bounded tree.
- [mops wrapper](wrapper.md): Write a project-local mops wrapper using Gradle-discovered paths.
- [mops skill](skill.md): Locate bundled agent skills.
- [mops explain](explain.md): Explain mops notations such as node references and edit batches.
- [mops examples](examples.md): Read bundled task recipes without starting MPS.

## mops

The global options listed here are accepted by every command. The command pages leave them out of their help output.

```text
Usage: mops [-hV] [COMMAND]
Kotlin CLI for the daemon-backed MPS prototype.
Commands:
  find      Search editable MPS project sources or loaded concept hierarchies.
  get       Get MPS project data.
  render    Render MPS project data.
  edit      Edit MPS project data.
  check     Run MPS checks.
  make      Generate and compile modules or the whole project.
  create    Create a project module or model.
  diagnose  Diagnose how the daemon loaded the MPS project.
  daemon    Inspect or control mops daemon processes.
  code      Run trusted Groovy against an open MPS project.
  test      Build and run MPS tests in a separate process.
  list, ls  List an MPS navigation target as a bounded tree.
  wrapper   Write a project-local mops wrapper using Gradle-discovered paths.
  skill     Locate bundled agent skills.
  explain   Explain mops notations such as node references and edit batches.
  examples  Read bundled task recipes without starting MPS.
  help      Display help information about the specified command.
Options:
  -h, --help                Show this help message and exit.
  -V, --version             Print version information and exit.
Global options:
      --daemon-home=PATH    Daemon home used by daemon-backed commands.
      --java-home=PATH      Java home used to start daemon-backed commands.
      --macro=NAME=PATH     Path macro for MPS, such as one used as ${NAME} in
                              .mps/libraries.xml or .mps/modules.xml;
                              repeatable. $PROJECT_DIR$ needs no macro. See
                              mops wrapper --help.
      --mps-home=PATH       MPS home used by daemon-backed commands.
      --project-root=PATH   Explicit MPS project root, absolute or relative to
                              the directory where mops was started. Overrides
                              upward inference from the working directory.
      --refs-as-urls        Render node references as MPS URLs in text output;
                              JSON output is unchanged.

Global options can appear before, between, or after command names; command
options belong after their command name. mops explain output describes how
output separates results, metadata, and diagnostics.

Start with mops examples for task recipes; mops examples editing helps choose
an editing approach.
```

## Notations

- [mops explain code](explain/code.md): execute Groovy code over an open MPS project.
- [mops explain edit](explain/edit.md): the edit-batch notation: an ordered batch of edit operations applied as a unit,
  with best-effort atomicity.
- [mops explain inline-subtree](explain/inline-subtree.md): the node-tree an edit operation builds: fresh nodes, move
  leaves, and copy leaves.
- [mops explain target](explain/target.md): how an edit operation names a node.
- [mops explain position](explain/position.md): where a child sits among the children of a containment role.
- [mops explain node-ref](explain/node-ref.md): the node reference notation.
- [mops explain name-pattern](explain/name-pattern.md): the Name Pattern notation for `mops find root-by-name`.
- [mops explain scope](explain/scope.md): the search scope clause on `mops find`.
- [mops explain output](explain/output.md): how command output separates results, metadata, and diagnostics.
- [mops explain edit.addChild](explain/edit.addChild.md): create a new child node under a containment role.
- [mops explain edit.addRoot](explain/edit.addRoot.md): create a new root node directly in a model.
- [mops explain edit.copyAsChild](explain/edit.copyAsChild.md): copy a node subtree under a containment role.
- [mops explain edit.copyAsRoot](explain/edit.copyAsRoot.md): copy a node subtree into a model as a new root node.
- [mops explain edit.delete](explain/edit.delete.md): delete a node.
- [mops explain edit.deleteChild](explain/edit.deleteChild.md): delete a child selected by role and position.
- [mops explain edit.moveAsChild](explain/edit.moveAsChild.md): move an existing node to a new parent role.
- [mops explain edit.moveAsRoot](explain/edit.moveAsRoot.md): move an existing node to root node position of a model.
- [mops explain edit.replace](explain/edit.replace.md): swap a node in place for a new subtree, keeping its exact slot.
- [mops explain edit.setProperty](explain/edit.setProperty.md): set or clear a single property on a node.
- [mops explain edit.setReference](explain/edit.setReference.md): point or clear a node's reference.
- [mops explain edit.unwrap](explain/edit.unwrap.md): strip a wrapper, keeping one descendant in its place.
- [mops explain edit.wrap](explain/edit.wrap.md): put a new node around an existing one, keeping its exact slot.

## Skills and task recipes

- [Working with MPS through mops](../../skills/mops/SKILL.md)
- [Mops examples](../../skills/mops/references/README.md)
- [Create modules and models](../../skills/mops/references/editing.creation.md)
- [Insert Java as BaseLanguage nodes](../../skills/mops/references/editing.java.md)
- [JSON edits](../../skills/mops/references/editing.json.md)
- [Editing: choose an approach](../../skills/mops/references/editing.md)
- [Code Mode: run compact Groovy programs](../../skills/mops/references/editing.nodes.md)
- [Retarget references and update model imports](../../skills/mops/references/editing.references.md)
- [Navigate and read](../../skills/mops/references/navigation.md)
- [Load runtime and plugin classes](../../skills/mops/references/runtime.md)
- [Search](../../skills/mops/references/search.md)
- [Setup and discover the API](../../skills/mops/references/setup.md)
- [Build, check, test, and recover](../../skills/mops/references/validation.md)
- [Working with mops daemons](../../skills/mops-daemon/SKILL.md)
