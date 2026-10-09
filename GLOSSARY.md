# mops

mops helps users and agents inspect and work with JetBrains MPS projects from a CLI. This glossary keeps MPS project
vocabulary precise when discussing navigation, lookup, and model operations.

## Language

**MPS Project**: The JetBrains MPS project the user is working in. An **MPS Project** owns zero or more **Project
Modules**. _Avoid_: current project when precision matters _Related_: MPS Repository, source repository, checkout

**Project Name**: The canonical name of an **MPS Project**. _Related_: project directory name

**MPS Module**: A top-level MPS unit visible to an MPS project session, such as a language, solution, or generator.
_Avoid_: module when the owning scope is unclear

**Module Name**: The canonical name of an **MPS Module**. _Related_: Module Reference

**Project Module**: An **MPS Module** that belongs to an **MPS Project**, including its generators when they are part of
that project. Dependency and platform modules available alongside the project are not **Project Modules**. _Related_:
dependency module, platform module

**Module Creation Operation**: A project-level operation that creates a language, solution, devkit, or generator as a
**Project Module**. _Avoid_: generic create-module operation _Related_: Project Module, Code Mode Extension, Module
Creation Report

**Model Creation Operation**: An operation that creates an **MPS Model** in a selected **Project Module**. _Related_:
MPS Model, Project Module, Code Mode Extension

**Module Creation Report**: The structured CLI result of a **Module Creation Operation**, identifying the primary module
and every companion artifact created with it. _Related_: Module Creation Operation, Project Module

**Solution Usage Preset**: A named configuration of a solution's generation-target facets according to its intended use.
_Avoid_: solution kind, raw facet options _Related_: Module Creation Operation, Project Module

**Embedded Generator**: A generator whose descriptor is contained in its source language's descriptor. It is tied to
that language. _Avoid_: Language-owned Generator _Related_: Standalone Generator, Project Module

**Standalone Generator**: A generator with its own descriptor that remains tied to a source language. "Standalone"
describes descriptor persistence, not the absence of a language relationship. _Related_: Embedded Generator, Project
Module

**MPS Repository**: The complete MPS repository visible to an MPS project session, including **Project Modules** and
dependency or platform modules. _Related_: Git repository, source repository

**Project and Libraries**: The **MPS Project** together with the library modules visible to that project. _Related_: MPS
Repository

**Editable Project Sources**: The editable **MPS Models** in an **MPS Project** that users and agents may modify.
Non-editable stub models and packaged library models are not **Editable Project Sources**. _Related_: Project and
Libraries

**MPS Model**: A named model contained in an **MPS Module**. An **MPS Model** owns zero or more **Root Nodes**.
_Related_: model file

**MPS Concept**: A language concept that classifies an **MPS Node**. _Related_: Subconcept

**Subconcept**: An **MPS Concept** that directly or transitively specializes another **MPS Concept**. _Related_: MPS
Concept

**Concept Instance**: An **MPS Node** classified by an **MPS Concept**. When an **MPS Concept** is considered through
its specialization hierarchy, instances of its **Subconcepts** are also **Concept Instances** of that concept.
_Related_: MPS Concept, Subconcept

**Model Name**: The full name of an **MPS Model**, including its stereotype when present. _Related_: long name, model
path

**Model Reference**: A globally usable reference to an **MPS Model**. _Related_: model id, model path

**Root Node**: An **MPS Node** owned directly by an **MPS Model** rather than by another node. _Avoid_: root element,
top-level node

**MPS Node**: A model element in an **MPS Model**. An **MPS Node** may have links to other **MPS Nodes** and zero or
more **Children**. _Avoid_: AST node when precision matters

**Build Project**: An MPS build-language root that describes which MPS modules participate in a build and records
extracted information about those modules. _Related_: MPS Node

**Node Name**: The name value of a named **MPS Node**. A **Node Name** is distinct from a **Node Presentation**.
_Related_: Node Presentation

**Node Presentation**: The user-facing presentation of an **MPS Node**, which may differ from its **Node Name**.
_Related_: Node Name

**MPS Link**: A named relationship from one **MPS Node** to another **MPS Node**. An **MPS Link** is either a
**Containment Link** or a **Reference Link**. _Avoid_: edge, pointer

**Role**: The name of an **MPS Link**. _Related_: property

**Child**: An **MPS Node** owned by another **MPS Node** through a **Containment Link**. _Related_: reference target,
property

**Containment Link**: An **MPS Link** that owns another **MPS Node** as a **Child**. Also called a child link or
aggregation link. _Related_: Reference Link

**Reference Link**: An **MPS Link** that allows a source **MPS Node** to point to a target **MPS Node** without owning
it. _Related_: Containment Link

**Reference**: A non-owning relationship instance from a source **MPS Node** to a target **MPS Node** through a
**Reference Link**. A **Reference** records the identity of the target node. _Related_: Reference Link

**Node Usage**: A **Reference** whose target is the **MPS Node** being searched for. A **Node Usage** has an owning
source **MPS Node**, a **Role** identifying the **Reference Link**, and the searched target **MPS Node**. _Avoid_: node
reference when describing usage _Related_: Node Reference, Reference

**Node ID**: An identifier for an **MPS Node** that is unique only within its **MPS Model**. Its decimal and encoded
spellings identify the same node. _Related_: Node Reference

**Node Reference**: A globally usable reference to an **MPS Node** that combines a **Model Reference** with a **Node
ID**. _Related_: Node ID, path

**Module Reference**: A globally usable reference to an **MPS Module**. _Related_: module id

**Concept Name**: A name for an **MPS Concept**, expressed as its qualified name, persisted name, or bare short name.
_Related_: MPS Concept, Navigation Target

**Navigation Target**: A user-supplied path of names or serialized references identifying the **MPS Repository**, an
**MPS Module**, an **MPS Model**, a **Root Node**, or an **MPS Node**. _Avoid_: path, target when precision matters
_Related_: Node Reference, Model Reference, Module Reference, Search Scope

**Search Scope**: The portion of the **MPS Repository** considered by a **Repository Search**. It may consist of
**Editable Project Sources** or the contents of an explicit **Navigation Target**. _Avoid_: search space, context
_Related_: Editable Project Sources, Navigation Target

**Repository Lookup**: Resolution of a supplied name, reference, or **Navigation Target** to zero or one object. An
ambiguous identity has no unique lookup result. _Related_: Repository Search, Concept Name, Node Reference, Model
Reference, Module Reference

**Repository Search**: Discovery of zero or more objects satisfying criteria within a **Search Scope**. Criteria may
describe names, concept instances, or usages; name matching may be exact, pattern-based, or fuzzy. _Related_: Repository
Lookup, Search Scope, Concept Instance, Node Usage

### Test Execution

**Test Selection**: The root test case, **MPS Model**, **MPS Module**, or **MPS Project** chosen for test execution.
_Related_: Navigation Target, Test Run

**Test Run**: An execution of the tests identified by a **Test Selection**, including results for completed tests when
execution is interrupted. _Related_: Test Selection, Test Run Report

**Test Run Report**: The recorded outcome of a **Test Run**, containing individual test results, failure details, and
source identities. A report for an interrupted run retains completed results and identifies the run as incomplete.
_Related_: Test Run, Node Reference

### Model Editing

**Java Snippet Parsing**: Conversion of Java source text into **MPS Nodes** at a supplied destination, including
reference and structural ambiguity resolution. _Related_: MPS Node, Code Mode, Reference, Java Parsing Result

**Java Parsing Result**: The outcome of **Java Snippet Parsing**, consisting of the inserted nodes and any unresolved
references or ambiguous constructs within them. _Related_: Java Snippet Parsing, MPS Node, Reference

**Edit Operation**: A single modification to an **MPS Node** in **Editable Project Sources**: a primitive change
(setting a property, setting a **Reference**, adding, deleting, moving, or copying a node) or an **Intent Operation**.
_Avoid_: mutation, change _Related_: Editable Project Sources, Node Subtree, Constraint, Intent Operation

**Intent Operation**: An **Edit Operation** that expresses a rearrangement: replacing a node, wrapping it, or unwrapping
a survivor from it. _Avoid_: macro, compound edit _Related_: Edit Operation, Move Leaf, Inline Subtree

**Inline Subtree**: A node-tree specification for new structure in an **Edit Operation**. Each position holds a
fresh-node specification, a **Move Leaf**, or a **Copy Leaf**. _Avoid_: nested children, template _Related_: Edit
Operation, Move Leaf, Copy Leaf

**Move Leaf**: A leaf in an **Inline Subtree** representing adoption of an existing **MPS Node** and its **Node
Subtree** into new structure. The adopted node retains its identity. _Related_: Inline Subtree, Copy Leaf, Node Subtree

**Copy Leaf**: A leaf in an **Inline Subtree** representing a copy of an existing **MPS Node**'s **Node Subtree** with
fresh identities and internal **References** directed to the copied nodes. _Related_: Inline Subtree, Move Leaf

**Node Subtree**: An **MPS Node** together with all nodes reachable from it through **Containment Links**. Copying a
node "with descendants" copies its **Node Subtree**. _Avoid_: node tree, branch _Related_: Containment Link, Child

**Code Mode**: An environment in which users and agents compose operations against an open **MPS Project** using native
MPS objects and **Code Mode Extensions** within one program. _Avoid_: Edit Script, eval mode _Related_: MPS Project,
Access Block, Edit Operation, Code Mode Extension

**Access Block**: An independent, non-nesting section of a **Code Mode** program with read access or command access to
an **MPS Project**. _Avoid_: transaction when the block is read-only _Related_: Code Mode, MPS Project, Edit Operation

**Code Mode Reference**: The discoverable documentation of **Code Mode Extensions** available to an **MPS Project**,
together with the native MPS members usable alongside them. _Avoid_: command help, service registry _Related_: Code
Mode, Code Mode Extension, Code Mode Namespace, MPS Project

**Code Mode Namespace**: The hierarchy that groups project-wide **Code Mode Extensions** and concept-specific operations
by purpose. _Avoid_: services, service registry, utility namespace _Related_: Code Mode, Code Mode Extension, MPS
Project

**Code Mode Extension**: A mops-supported convenience on a native MPS object or in the **Code Mode Namespace**. Its
contract belongs to mops rather than to the selected MPS version. _Avoid_: handle, wrapper _Related_: Code Mode, Code
Mode Extension Bundle, Code Mode Namespace, MPS Node, MPS Model, MPS Module

**Code Mode Extension Bundle**: A built-in or plugin contribution that groups **Code Mode Extensions** with their **Code
Mode Reference** documentation under a bundle identity and version. _Avoid_: service provider _Related_: Code Mode, Code
Mode Extension, Code Mode Reference

**Materialize**: An **Edit Operation** materializes an **MPS Node** when it brings the node into its model fresh or as a
copy. A **Move Leaf** does not materialize the node it adopts — that node already existed and keeps its identity.
_Related_: Edit Operation, Copy Leaf, Move Leaf, Edit Plugin

**Edit Plugin**: Language-aware behavior that adjusts model state when an **Edit Operation** materializes a **Concept
Instance** of a declared concept. Its automatic adjustments are disclosed through **Hints**. _Avoid_: plugin without
qualification (MPS languages have a plugin aspect), hook _Related_: Materialize, Hint, Concept Instance

**Hint**: A free-text disclosure in an edit response describing something done automatically on the user's behalf, such
as an **Edit Plugin**'s adjustment. A **Hint** reports work done, not a problem — it is distinct from a warning.
_Avoid_: warning, notice _Related_: Edit Plugin

**Constraint**: A language-defined rule that restricts whether an edit is well-formed, such as which **MPS Concepts**
may fill an **MPS Link**, a link's cardinality, or whether a node may be a **Child** of another node. _Avoid_: rule,
validation _Related_: Constraint Violation, Model Check

**Constraint Violation**: A **Constraint** that a proposed **Edit Operation** would break. _Related_: Constraint

**Model Check**: The full validation of **MPS Models** and their owning **MPS Modules**, including typesystem, model,
module, and checking rules. _Avoid_: validation, type check _Related_: Constraint, MPS Model, Project Module

### CLI Help

**Command Help**: Usage text for a mops CLI command, describing its options, arguments, and subcommands. _Related_:
Explain Topic

**Notation**: A textual format that mops exchanges with users and agents, such as an edit batch or a serialized **Node
Reference**. _Avoid_: format, syntax when precision matters _Related_: Explain Topic

**Explain Topic**: A named, self-contained reference page about a **Notation** or part of one. _Avoid_: help topic, doc
page _Related_: Notation, Command Help
