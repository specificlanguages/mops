# mops

mops helps users and agents inspect and work with JetBrains MPS projects from a CLI. This glossary keeps MPS project vocabulary precise when discussing navigation, lookup, and model operations.

## Language

**MPS Project**:
The JetBrains MPS project the user is working in. An **MPS Project** owns zero or more **Project Modules**.
_Avoid_: current project when precision matters
_Related_: MPS Repository, source repository, checkout

**Project Name**:
The canonical name of an **MPS Project**.
_Related_: project directory name

**MPS Module**:
A top-level MPS unit visible to an MPS project session, such as a language, solution, or generator.
_Avoid_: module when the owning scope is unclear

**Module Name**:
The canonical name of an **MPS Module**.
_Related_: Module Reference

**Project Module**:
An **MPS Module** that belongs to an **MPS Project**, including its generators when they are part of that project. Dependency and platform modules available alongside the project are not **Project Modules**.
_Related_: dependency module, platform module

**MPS Repository**:
The complete MPS repository visible to an MPS project session, including **Project Modules** and dependency or platform modules.
_Related_: Git repository, source repository

**Project and Libraries**:
The **MPS Project** together with the library modules visible to that project.
_Related_: MPS Repository

**Editable Project Sources**:
The editable **MPS Models** in an **MPS Project** that users and agents may modify. Non-editable stub models and packaged library models are not **Editable Project Sources**.
_Related_: Project and Libraries

**MPS Model**:
A named model contained in an **MPS Module**. An **MPS Model** owns zero or more **Root Nodes**.
_Related_: model file

**MPS Concept**:
A language concept that classifies an **MPS Node**.
_Related_: Subconcept

**Subconcept**:
An **MPS Concept** that directly or transitively specializes another **MPS Concept**.
_Related_: MPS Concept

**Concept Instance**:
An **MPS Node** classified by an **MPS Concept**. When an **MPS Concept** is considered through its specialization hierarchy, instances of its **Subconcepts** are also **Concept Instances** of that concept.
_Related_: MPS Concept, Subconcept

**Model Name**:
The full model name value, including stereotype when present. mops treats this value as the model's name and does not use the long name without stereotype for lookup.
_Related_: long name, model path

**Model Reference**:
A globally usable reference to an **MPS Model**.
_Related_: model id, model path

**Root Node**:
An **MPS Node** owned directly by an **MPS Model** rather than by another node.
_Avoid_: root element, top-level node

**MPS Node**:
A model element in an **MPS Model**. An **MPS Node** may have links to other **MPS Nodes** and zero or more **Children**.
_Avoid_: AST node when precision matters

**Node Name**:
The name value of a named **MPS Node**. A **Node Name** is distinct from a **Node Presentation**.
_Related_: Node Presentation

**Node Presentation**:
The user-facing presentation of an **MPS Node**, which may differ from its **Node Name**.
_Related_: Node Name

**MPS Link**:
A named relationship from one **MPS Node** to another **MPS Node**. An **MPS Link** is either a **Containment Link** or a **Reference Link**.
_Avoid_: edge, pointer

**Role**:
The name of an **MPS Link**.
_Related_: property

**Child**:
An **MPS Node** owned by another **MPS Node** through a **Containment Link**.
_Related_: reference target, property

**Containment Link**:
An **MPS Link** that owns another **MPS Node** as a **Child**. Also called a child link or aggregation link.
_Related_: Reference Link

**Reference Link**:
An **MPS Link** that allows a source **MPS Node** to point to a target **MPS Node** without owning it.
_Related_: Containment Link

**Reference**:
A non-owning relationship instance from a source **MPS Node** to a target **MPS Node** through a **Reference Link**. A **Reference** records the identity of the target node.
_Related_: Reference Link

**Node Usage**:
A **Reference** whose target is the **MPS Node** being searched for. A **Node Usage** has an owning source **MPS Node**, a **Role** identifying the **Reference Link**, and the searched target **MPS Node**.
_Avoid_: node reference when describing usage
_Related_: Node Reference, Reference

**Node ID**:
An identifier for an **MPS Node** that is unique only within its **MPS Model**. A **Node ID** has two accepted spellings — the decimal form mops prints and the encoded form persisted in model files. Both name the same identifier and resolve wherever a **Node ID** appears.
_Related_: Node Reference

**Node Reference**:
A globally usable reference to an **MPS Node** that combines a **Model Reference** with a **Node ID**. A serialized **Node Reference** is half-opaque: users and agents read its parts to orient themselves but copy it whole from mops output rather than constructing it from pieces.
_Related_: Node ID, path

**Module Reference**:
A globally usable reference to an **MPS Module**. mops does not assume a separate short module identifier.
_Related_: module id

**Concept Name**:
A user-supplied name for an **MPS Concept**: its qualified name, the persisted spelling with a `.structure.` infix, or its bare short name. All spellings name the same concept; resolution counts matches across loaded languages rather than judging shape, and an ambiguous short name is reported with the qualified candidates.
_Related_: MPS Concept, Navigation Target

**Navigation Target**:
A user-supplied path of segments that resolves to the **MPS Repository**, an **MPS Module**, an **MPS Model**, a **Root Node**, or an **MPS Node**. A segment may be a name or a serialized reference. Resolution counts actual matches rather than judging a segment's shape; an ambiguous segment is reported with each candidate's serialized reference, never guessed.
_Avoid_: path, target when precision matters
_Related_: Node Reference, Model Reference, Module Reference, Search Scope

**Search Scope**:
The portion of the **MPS Repository** that a search considers. The default **Search Scope** is **Editable Project Sources**. An explicit **Search Scope** is named by a **Navigation Target** and is searched exhaustively, including non-editable content within it.
_Avoid_: search space, context
_Related_: Editable Project Sources, Navigation Target

### Model Editing

**Edit Operation**:
A single modification to an **MPS Node** in **Editable Project Sources**: a primitive change (setting a property, setting a **Reference**, adding, deleting, moving, or copying a node) or an **Intent Operation**.
_Avoid_: mutation, change
_Related_: Editable Project Sources, Node Subtree, Constraint, Intent Operation

**Intent Operation**:
An **Edit Operation** that names a rearrangement — replacing a node, wrapping it, or unwrapping a survivor from it. Its guarantees: the result takes the replaced node's exact place, every **Move Leaf** and kept node is detached before anything is deleted, and a target **Root Node** yields a **Root Node** of the same model.
_Avoid_: macro, compound edit
_Related_: Edit Operation, Move Leaf, Inline Subtree

**Inline Subtree**:
The node-tree specification an **Edit Operation** accepts for building new structure, shaped like the tree `get-node` emits. A position in an **Inline Subtree** holds either a fresh-node spec, a **Move Leaf**, or a **Copy Leaf**.
_Avoid_: nested children, template
_Related_: Edit Operation, Move Leaf, Copy Leaf

**Move Leaf**:
A leaf in an **Inline Subtree** that adopts an existing **MPS Node** together with its **Node Subtree** into the new structure. The adopted node keeps its identity, so inbound **References** keep resolving. Every **Move Leaf** is detached from its old home before any node deletion in the same **Edit Operation**.
_Related_: Inline Subtree, Copy Leaf, Node Subtree

**Copy Leaf**:
A leaf in an **Inline Subtree** that deep-copies an existing **MPS Node**'s **Node Subtree** into the new structure with fresh identities, rewiring intra-subtree **References** to the copy.
_Related_: Inline Subtree, Move Leaf

**Node Subtree**:
An **MPS Node** together with all nodes reachable from it through **Containment Links**. Copying a node "with descendants" copies its **Node Subtree**.
_Avoid_: node tree, branch
_Related_: Containment Link, Child

**Constraint**:
A language-defined rule that restricts whether an edit is well-formed, such as which **MPS Concepts** may fill an **MPS Link**, a link's cardinality, or whether a node may be a **Child** of another node.
_Avoid_: rule, validation
_Related_: Constraint Violation, Model Check

**Constraint Violation**:
A **Constraint** that a proposed **Edit Operation** would break.
_Related_: Constraint

**Model Check**:
The full validation of an **MPS Model**, including typesystem and checking rules. It is distinct from the cheaper **Constraint** evaluation and is performed as its own operation because it may be costly.
_Avoid_: validation, type check
_Related_: Constraint

### CLI Help

**Command Help**:
Usage text for a CLI command: its options, arguments, and subcommands. Requesting **Command Help** always succeeds and never requires a daemon or project.
_Related_: Explain Topic

**Notation**:
A textual format that mops exchanges with users and agents, such as the edit batch JSON or the serialized **Node Reference** syntax. Notations are what `mops explain` documents; CLI options belong to **Command Help** instead.
_Avoid_: format, syntax when precision matters
_Related_: Explain Topic

**Explain Topic**:
A named reference page about a **Notation** or a part of one, addressable by a dot path such as `edit` or `edit.copyAsChild`. An **Explain Topic** is small, self-contained, and names the related topics a reader may drill into.
_Avoid_: help topic, doc page
_Related_: Notation, Command Help

## Example Dialogue

Dev: Should project contents include MPS.Core?

Domain expert: MPS.Core may be visible in the **MPS Repository**, but it is not a **Project Module** owned by the **MPS Project**. When discussing both the project and its project-visible libraries, say **Project and Libraries**.

Dev: What should a project-level listing show?

Domain expert: It should show the **Project Modules** owned by the **MPS Project**.

Dev: Should a command for finding code to modify search project-visible libraries and stub models?

Domain expert: No. It should search **Editable Project Sources** so only editable models that users and agents may modify are returned.

Dev: When searching for instances of an MPS concept, do subconcepts count?

Domain expert: Yes. An instance of a **Subconcept** may be treated as a **Concept Instance** of the ancestor **MPS Concept**.

Dev: Can I identify a node with just its ID?

Domain expert: Only inside a known **MPS Model**. For output and global lookup, use a **Node Reference**.

Dev: Should path lookup use the text MPS shows in the editor?

Domain expert: No. Path lookup uses **Node Name**, not **Node Presentation**.

Dev: Should lookup ignore a model stereotype if the base name matches?

Domain expert: No. A model stereotype is part of the **Model Name**.

Dev: Does listing a node include its references?

Domain expert: No. Listing a node follows **Containment Links** only, so it shows **Children** and the **Roles** of those links.

Dev: Is a node reference the same thing as a usage?

Domain expert: No. A **Node Reference** is a serialized identity for an **MPS Node**. A **Node Usage** is a **Reference** owned by a source node and pointing to the target node being searched for.

Dev: If a search is scoped to a read-only library model, do we skip it because it is not editable?

Domain expert: No. An explicit **Search Scope** is searched exhaustively. The **Editable Project Sources** restriction applies only to the default scope.

Dev: If foo.bar.baz names both a module and a model, do we resolve to the module because it looks like a module name?

Domain expert: No. **Navigation Target** resolution never decides by shape. It reports the ambiguity with each candidate's serialized reference so the user can retry unambiguously.

Dev: An agent pasted a node id copied from a model file and we answered "node not found". Is that right?

Domain expert: No. Both spellings of a **Node ID** name the same identifier and must resolve. And since a bare **Node ID** is unique only within its **MPS Model**, a lookup by bare id reports every matching node with its full **Node Reference** rather than guessing one.

Dev: When we edit a model, do we run a full model check?

Domain expert: No. An **Edit Operation** evaluates **Constraints**, which is cheap. A full **Model Check**, including the typesystem, is a separate operation because it may be costly.

Dev: If an edit is forced through despite breaking a rule, do we stay quiet about it?

Domain expert: No. mops reports **Constraint Violations** whether or not they block the **Edit Operation**.

Dev: Is deleting a node a different act from detaching it?

Domain expert: No. Deleting an **MPS Node** means detaching it so it is no longer reachable through its **MPS Model** — there is no separate destruction step. That is why a node adopted out of deleted structure by a **Move Leaf** simply survives, identity intact.

Dev: When a replace deletes a node that something else references, do we hunt down the dangling references?

Domain expert: No. Dangling references are a reference-resolution concern, which belongs to **Model Check**, not to **Constraint** evaluation — the same split we made for edits generally. A **Move Leaf** preserves node identity, so reusing a node instead of rebuilding it is how a caller keeps inbound references alive.

Dev: When we wrap a node that carries an annotation, does the annotation end up on the wrapper?

Domain expert: No. An annotation is a **Child** of the annotated node, so it travels with the wrapped node's **Node Subtree**. Moving it onto the wrapper is a separate, explicit edit.

Dev: Does copying a node bring its references along?

Domain expert: Copying a node copies its **Node Subtree**, which follows **Containment Links** only. A **Reference** whose target lies inside the copied subtree is rewired to point at the corresponding copied node, so the copy is self-contained. A **Reference** to a node outside the subtree keeps pointing at that original target, since it is not itself copied.
