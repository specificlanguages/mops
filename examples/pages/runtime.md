# Load runtime and plugin classes

`mops.runtime` loads compiled classes through an **MPS Module**'s class loader: the module's own generated classes plus
those of its dependencies. Use it to reach language runtime singletons, generated behavior or constraint descriptors,
and plugin solution code without touching MPS's classloading internals. Name the module as for `mops.lookup.module`:
its name or serialized reference, resolved uniquely.

{{table:classes|Groovy program}}

Both operations need `project.read` or `project.command`. `loadClass` returns null only when the module is fine and
the class is missing; `requireClass` throws instead. Module problems are errors with a named case: unknown module,
ambiguous module, module without a class loader (a devkit), and module whose runtime is not loaded. A source module
whose compiled output is absent reports "not compiled" and names the `mops make module <module>` command to run first.
Making a module inside the daemon reloads its classes; the reloaded class loader serves the next access block.

{{table:commands|Command}}

## Language runtime singletons

Runtime solutions compile with the language that uses them, so load their classes through either module:

```groovy
{{singleton}}
```

The returned `Class` belongs to the module's class loader. Groovy dispatches calls on instances dynamically, so
`instance.entries` works as in the example. Convert results to strings, numbers, maps, or MPS nodes before returning
them from the program.

## Generated code that takes nodes

MPS erases `node<Concept>` parameters to `org.jetbrains.mps.openapi.model.SNode`, so reflective lookups use `SNode` as
the parameter type and accept any node:

```groovy
{{behavior}}
```

## Plugin solutions

A plugin solution's classes load the same way once the solution is compiled. For a solution whose classes are
contributed by an installed IDE plugin, the module's class loader is that plugin's:

```groovy
{{plugin}}
```
