# Load runtime and plugin classes

`mops.runtime` loads compiled classes through a module's class loader: the module's own generated classes plus
those of its dependencies. Use it to reach language runtime singletons, generated behavior or constraint descriptors,
and plugin solution code without touching MPS's classloading internals. Name the module as for `mops.lookup.module`:
its name or serialized reference, resolved uniquely.

| Task                                        | Groovy program                                                                                                                                                                                                                                                                                             |
| ------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Load a class from a module's class loader   | `project.read { mops.runtime.requireClass('jetbrains.mps.baseLanguage', 'jetbrains.mps.baseLanguage.behavior.ClassConcept__BehaviorDescriptor').name }`                                                                                                                                                    |
| Detect a missing class without an exception | `project.read { mops.runtime.loadClass('jetbrains.mps.baseLanguage', 'jetbrains.mps.baseLanguage.behavior.NoSuchClass') == null }`                                                                                                                                                                         |
| Call a static method that takes a node      | `project.read { def operations = mops.runtime.requireClass('jetbrains.mps.lang.smodel', 'jetbrains.mps.lang.smodel.generator.smodelAdapter.SNodeOperations'); operations.getMethod('getConcept', org.jetbrains.mps.openapi.model.SNode).invoke(null, mops.lookup.requireNode('NODE_REF')).qualifiedName }` |

Both operations need `project.read` or `project.command`. `loadClass` returns null only when the module is fine and
the class is missing; `requireClass` throws instead. Module problems are errors with a named case: unknown module,
ambiguous module, module without a class loader (a devkit), and module whose runtime is not loaded. A source module
whose compiled output is absent reports "not compiled" and names the `mops make module <module>` command to run first.
Making a module inside the daemon reloads its classes, and programs run afterwards load from the new class loader.

| Task                                                | Command                                |
| --------------------------------------------------- | -------------------------------------- |
| Compile a project module before loading its classes | `mops make module sample.language`     |
| Inspect the runtime helpers                         | `mops code help mops.runtime`          |
| Explain why a module's runtime is not loaded        | `mops diagnose module sample.language` |

## Language runtime singletons

A language's class loader delegates to its runtime solutions, so a runtime class loads through the language or
through the runtime solution itself:

```groovy
project.read {
    def registry = mops.runtime.requireClass('sample.language', 'sample.language.runtime.Registry')
    def instance = registry.getMethod('getInstance').invoke(null)
    instance.entries.collect { it.toString() }
}
```

The returned `Class` belongs to the module's class loader. Groovy dispatches calls on instances dynamically, so
`instance.entries` works as in the example. Convert results to strings, numbers, maps, or MPS nodes before returning
them from the program.

## Generated code that takes nodes

MPS erases `node<Concept>` parameters to `org.jetbrains.mps.openapi.model.SNode`, so reflective lookups use `SNode` as
the parameter type and accept any node:

```groovy
import org.jetbrains.mps.openapi.model.SNode
project.read {
    def node = mops.lookup.requireNode('NODE_REF')
    def evaluator = mops.runtime.requireClass('sample.language', 'sample.language.runtime.Evaluator')
    evaluator.getMethod('evaluate', SNode).invoke(evaluator.getDeclaredConstructor().newInstance(), node).toString()
}
```

## Plugin solutions

A plugin solution's classes load the same way once the solution is compiled:

```groovy
import org.jetbrains.mps.openapi.model.SModel
project.read {
    def model = mops.lookup.requireModel('sample.model')
    def statistics = mops.runtime.requireClass('sample.plugin', 'sample.plugin.util.ModelStatistics')
    statistics.getMethod('count', SModel).invoke(null, model)
}
```
