# mops get

Generated from `mops get --help` by `./gradlew :cli:updateReference`. Do not edit this page. Global options are listed
in the [index](index.md).

Get MPS project data.

```text
Usage: mops get [-h] [COMMAND]
Get MPS project data.
Commands:
  node    Export one MPS node as JSON.
  module  Report where MPS locates one module's files.
  help    Display help information about the specified command.
Options:
  -h, --help                Show this help message and exit.
```

## mops get node

Export one MPS node as JSON.

```text
Usage: mops get node [OPTIONS] NODE_REFERENCE
       mops get node [OPTIONS] MODEL_TARGET NODE_ID
Export one MPS node as JSON.
Parameters:
      NODE_REFERENCE | MODEL_TARGET NODE_ID
                            Serialized node reference, or model target followed
                              by node ID.
Options:
      --ancestry            Nest `parent` recursively up to the root node,
                              giving the full containment chain, not just the
                              immediate parent.
      --compact             Print single-line JSON instead of the
                              pretty-printed default.
      --depth=N             Export N containment levels below the node in full
                              and the nodes at level N as stubs (role, concept,
                              name, reference, childCount); 0 leaves the
                              children out. Default is the whole subtree. Not
                              allowed with --summary.
  -h, --help                Show this help message and exit.
      --summary             Print only the node's type, name, concept,
                              reference, parent, and the number of children per
                              role. Not allowed with --depth.

An unresolved target fails with NODE_NOT_FOUND. The exported object carries the
node's concept, id, properties, references, and child subtree, plus a `parent`
object for its immediate containing node: the containment `role` by which the
node sits in it, a `type` of `root` or `node`, and the parent's name, concept,
and reference. A root node has no `parent`.

Output is pretty-printed with `concept`, `id`, and `parent` ahead of
`properties`, `references`, and `children`, so `head` shows the identifying
part first.

With --depth N, the top-level object gains `truncatedAt: N`, so a stub
(`childCount` present) is never mistaken for a leaf. --summary prints the same
summary object `find` emits (`type`, `name`, `concept`, `reference`, `parent`)
plus `children` as a role-to-count map, without serializing the subtree. When
neither is given and the export exceeds about 16 KB, a hint on stderr points at
both options.
```

## mops get module

Report where MPS locates one module's files.

```text
Usage: mops get module [-h] [--json] MODULE
Report where MPS locates one module's descriptor, jars, class output, and model
sources.
Parameters:
      MODULE                Module name or serialized module reference; library
                              modules included.
Options:
  -h, --help                Show this help message and exit.
      --json                Print the module's locations as JSON.

The module is found anywhere in the repository. Text output is one
`key<TAB>value` row per location MPS reports, keyed by the JSON field name:
`kind`, `name`, `reference`, `packaged`, then `descriptor` (the .mpl, .msd, or
.devkit file of a source module, or `name.jar!/META-INF/module.xml` of a
deployed one), `jar` and `sourcesJar` (the deployed module's archive and the
sources archive its deployment descriptor names), `sourceDescriptor` (the
source descriptor inside that archive), `sourceDirectory` (a source module's
descriptor directory), `classesGen` (a source module's compiled-class output,
when its Java facet declares one), `classPath` (one row per entry of the Java
facet's runtime classpath), and `modelRoots` (one row per file-based model
root).

A location MPS does not record for the module is left out rather than guessed
from a naming convention. --json prints the daemon response, with the same
fields under `module`. An unknown module fails with MODULE_NOT_FOUND; a name
shared by several modules fails and lists their serialized references, so a
reference disambiguates it.
```
