# Code Mode: run compact Groovy programs

Save a snippet as `task.groovy` and run `mops code run task.groovy`, or pass it on stdin:

```sh
printf '%s\n' 'project.read { project.projectModules.collect { it.moduleName } }' | mops code run -
```

The following snippets are complete programs. `NODE_REF`, `CLASS_REF`, `TARGET_REF`, and `STATEMENTS_REF` are
placeholders. Use a method node for the `body` child examples and a BaseLanguage `ClassifierType` node for the
`classifier` reference examples. Reads and lookups belong in `project.read`; writes belong in `project.command`. Blocks
cannot nest. Successful commands save changes; exceptions do not roll back partial changes. Each invocation has fresh
variables. Return useful data: strings print as text, maps/lists become JSON, and native nodes/models/modules become
serialized references.

| Task                            | Groovy program                                                                                                                                                                                                                   |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| List root names and references  | `project.read { mops.lookup.requireModel('sample.model').rootNodes.collect { [name: it.properties['name'], node: it] } }`                                                                                                        |
| Read a node property            | `project.read { mops.lookup.requireNode('NODE_REF').properties['name'] }`                                                                                                                                                        |
| Find a root with an exact name  | `project.read { mops.lookup.requireModel('sample.model').rootNodes.find { it.properties['name'] == 'Example' } }`                                                                                                                |
| List containment descendants    | `project.read { mops.lookup.requireNode('NODE_REF').descendants.collect { [concept: it.concept.qualifiedName, node: it] } }`                                                                                                     |
| List ancestors                  | `project.read { mops.lookup.requireNode('NODE_REF').ancestors }`                                                                                                                                                                 |
| List available property names   | `project.read { mops.lookup.requireNode('NODE_REF').concept.properties.collect { it.name } }`                                                                                                                                    |
| List child roles                | `project.read { mops.lookup.requireNode('NODE_REF').concept.containmentLinks.collect { it.name } }`                                                                                                                              |
| List reference roles            | `project.read { mops.lookup.requireNode('NODE_REF').concept.referenceLinks.collect { it.name } }`                                                                                                                                |
| Read one child                  | `project.read { mops.lookup.requireNode('NODE_REF').child['body'] }`                                                                                                                                                             |
| Read ordered children in a role | `project.read { mops.lookup.requireNode('CLASS_REF').children['member'] }`                                                                                                                                                       |
| Resolve a reference target      | `project.read { mops.lookup.requireNode('NODE_REF').references['classifier']?.targetNode }`                                                                                                                                      |
| Rename a node                   | `project.command { def n = mops.lookup.requireNode('NODE_REF'); n.properties['name'] = 'Renamed'; n }`                                                                                                                           |
| Change a reference target       | `project.command { def n = mops.lookup.requireNode('NODE_REF'); n.references['classifier'] = mops.lookup.requireNode('TARGET_REF'); n }`                                                                                         |
| Clear a child role              | `project.command { def n = mops.lookup.requireNode('NODE_REF'); n.child['body'] = null; n }`                                                                                                                                     |
| Reverse ordered members         | `project.command { def n = mops.lookup.requireNode('CLASS_REF'); n.children['member'] = n.children['member'].reverse(); n }`                                                                                                     |
| Create a class root             | `project.command { def m = mops.lookup.requireModel('sample.model'); def n = m.createNode(mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.ClassConcept')); n.properties['name'] = 'Example'; m.addRootNode(n); n }` |
| Create a solution and model     | `project.command { project.createSolution('sample.solution', [usagePreset: 'java']).createModel('sample.model') }`                                                                                                               |
| Render a node                   | `def n = project.read { mops.lookup.requireNode('NODE_REF') }; n.render()`                                                                                                                                                       |
| Inspect a runtime object's API  | `project.read { help(mops.lookup.requireNode('NODE_REF')) }`                                                                                                                                                                     |

`child[role]` rejects multiple children; use `children[role]` for lists. Newly attached children must be detached.
Indexed accessors shadow native Groovy getter properties. Use concept descriptors and native methods when necessary;
consult `mops code help SNode` for signatures. Code Mode child assignments do not check containment constraints.

For streaming searches with custom result data:

```groovy
project.read { def found = []; mops.search.eachInstanceOf(mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.ClassConcept'), project.scope) { found << [name: it.properties['name'], node: it] }; found }
```

```groovy
project.read { def found = []; mops.search.eachUsageOf(mops.lookup.requireNode('NODE_REF'), project.scope) { found << [role: it.link.name, source: it.sourceNode] }; found }
```
