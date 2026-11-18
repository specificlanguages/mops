# mops Design Standards

How mops code is divided into classes and packages. Each rule has a short name (for example **`value-immutable`**), so a
review comment can cite the rule instead of arguing the principle again.

In this document, **class** means a top-level `class`, Kotlin `interface` or `object`, or a file of top-level functions.
A private or nested helper belongs to the class that contains or uses it.

_Deep_, _shallow_ and the _deletion test_ mean what the
[`codebase-design` skill](../../.agents/skills/codebase-design/SKILL.md) says. Two words differ from that skill. Here,
**Adapter** is only the class kind that does I/O; the skill's adapter is anything that fills a seam, such as a test
fake. A Kotlin `interface` is always written that way, because the skill's _interface_ means everything a caller must
know.

## How to use this document

- **Authors**: check each class, package and operation that your change adds, splits or moves, or gives new I/O or
  policy, against every rule. You are done when each one follows every rule or states an exception (see Exceptions).
- **Reviewers**: cite a rule name in each design comment. A comment that cites no rule is a suggestion, not a
  requirement.
- **Exceptions**: break a rule only when following it makes the code clearly worse. State the rule name and the reason
  in the KDoc of the class that breaks it. An undocumented violation is a defect.

## Part 1: Class kinds

Each class belongs to one **kind**. The kind sets what the class may do.

| Kind            | What it is                                        | May do I/O?                                                             | May apply policy?                                          |
| --------------- | ------------------------------------------------- | ----------------------------------------------------------------------- | ---------------------------------------------------------- |
| **Value**       | Immutable data.                                   | No                                                                      | Only about its own validity                                |
| **Logic**       | Computes results and applies policy.              | No                                                                      | Yes                                                        |
| **Adapter**     | Talks to one external thing.                      | Yes                                                                     | No                                                         |
| **Entry point** | Starts an operation and wires the parts together. | Reads input, reaches MPS, takes MPS locks, writes results and log lines | Only about input and output (**`entry-delegates-policy`**) |
| **State**       | Mutable state that outlives one call.             | No                                                                      | No                                                         |

Dependencies point inward: code that does I/O depends on code that does no I/O, never the other way. Each kind's section
below lists what it may depend on.

**`one-kind`**. Every class has exactly one kind. If one seems to need two, split it. An Adapter with a cache
(**`adapter-cache`**) is still one kind.

**`kind-in-kdoc`**. Every class states its kind as the first word of its KDoc: `Value.`, `Logic.`, `Adapter.`,
`Entry point.` or `State.` For a file of top-level functions, put the KDoc above the `package` line. A `@Serializable`
data class, enum or sealed type is a Value and needs no kind word. A Kotlin `interface` that Logic defines for an
Adapter (**`logic-interface`**) is Logic. Any other Kotlin `interface` has the kind of the classes that implement it.

**I/O.** The Logic/Adapter split is the **Humble Object** pattern: code that touches the outside world is hard to test,
so keep it thin and move policy into Logic. Logic has three properties:

- **Deterministic**: the same inputs give the same output.
- **No effects**: calling it twice, or not at all, changes nothing outside it.
- **Visible inputs**: everything that can change between calls or runs arrives in its parameters.

These make Logic cheap to test and easy to read. **I/O** is anything that breaks one of them. Common examples: the
filesystem, processes, git, network, environment variables, system properties, the clock, randomness, stdout/stderr, and
mutable global state. Global access to something that cannot change during a run is not I/O: a stateless facade that
only converts or describes (`PersistenceFacade`, `MetaAdapterFactory`), a build-time constant (`MopsBuild.fullVersion`),
or a resource bundled with mops. A default parameter value that reads the environment
(`now: () -> Instant = Instant::now`) keeps the input visible, because a test passes its own.

**MPS objects.** Logic may read MPS through objects it receives as parameters. That is not I/O. It includes:

- moving from an MPS Node to its children, parent or reference targets (`SNode.getReferenceTarget`);
- lookups in an `SRepository` it receives (`SRepository.getModule`, `SModelReference.resolve(repository)`);
- reading an MPS Model, even when MPS first loads it from disk;
- reading an MPS registry (`ConceptRegistry`, `LanguageRegistry`, `ConceptDescendantsCache`) in code that receives
  `MpsRead` or `MpsWrite`.

Only an Adapter or Entry point may do these, because they are I/O:

- reaching MPS through a global that searches or holds mutable state (`MPSModuleRepository.getInstance()`,
  `FindUsagesFacade`);
- taking an MPS model lock, running an MPS command, or switching to the EDT;
- changing an MPS Model.

**`lock-in-type`**. Code that needs an MPS lock receives a receiver or parameter whose type proves the lock is held
(`MpsRead`, `MpsWrite`). Only the class that takes the lock creates that type. Then the compiler stops a write inside a
read.

**Policy and mechanism.** Policy is a branch that makes a choice. Mechanism does the work and makes no choice ("retry on
`EINTR`"). Applying a limit that the caller passes in (depth, count) is mechanism. There are two sorts of policy:

- **Domain policy**, such as "skip this MPS Module because it is a generator". It is a Behavior design decision (Part
  3), so it lives in Logic.
- **Input and output policy**, such as rejecting a missing argument. It lives in the Entry point
  (**`entry-delegates-policy`**).

To tell them apart, ask: would a sibling path (`docs/VISION.md`, principle 7), such as Code Mode or an edit batch, need
the same rule? If yes, it is domain policy. Input validation that several commands share (`require(limit >= 0)`) is
input policy: keep it in one helper.

**Framework-shaped code.** When a framework dictates a class's shape, follow the framework and apply these rules to the
code you write inside it:

- picocli option and parameter fields are input, not State. So are `@Spec` fields. Fields that picocli reads after
  `run()` (`exitCode`) are output, not State.
- A picocli `ITypeConverter` belongs to the CLI, beside the command that uses it, not in the Value it produces.
- Code that only configures a framework (picocli help layout) is an Entry point.
- A class that exists only because the framework requires it (a picocli command group, a Code Mode Namespace class)
  counts as passing **`earns-its-place`** and needs no `Hides:` line.
- A custom `KSerializer` belongs to its Value: it holds that Value's wire format, does no I/O, and sits in the same file
  as the Value (**`value-deps`**). A serializer for a type that mops does not define (`Path`) is Logic.
- An exception class is a Value, exempt from **`value-equality`**. Throwing a domain exception counts as returning a
  Value.
- Code Mode scripts are Groovy and carry no static types. A Code Mode namespace checks the MPS lock at runtime through
  script-scoped State instead of **`lock-in-type`**, and may return live MPS objects to the script.
- A framework callback that reports through a side effect (a detekt rule's `report()`) is Logic. That call is an allowed
  exception to **`logic-returns-values`**.

### Value

**`value-immutable`**. Values are immutable: all properties are `val` and have read-only types (`List`, `Set`, `Map`, or
another Value). Values take collections as given, without defensive copies. MPS objects (`SNode`, `SModel`, `SModule`)
are mutable, so a Value holds a Node Reference or Module Reference instead. A result that passes live MPS objects
between functions inside one lock is not a Value: keep it private to the class that holds the lock.

**`value-validates`**. An invalid Value cannot exist. Prefer types that cannot express the invalid state: a sealed type
in place of "exactly one of these fields is set". When the sealed type would need a custom `KSerializer` only to tell
its cases apart, keep the nullable fields and check them in `init`. Check the rest in an `init` block or a factory
function. A check that needs I/O (a path exists) belongs in the Adapter that creates the Value.

**`value-equality`**. Values have structural equality (`data class`, or `equals`/`hashCode` over all significant
properties).

**`value-no-io`**. Values do no I/O.

**`value-own-behavior`**. A Value may compute things from its own properties (comparison, derived properties), parse and
format its own text form, and build itself from other Values in a factory function. Anything that needs more belongs in
Logic, even when Kotlin convention would put it in the companion object.

**`value-deps`**. Values depend only on other Values, their serializers, and the Kotlin and JDK standard libraries.

**Protocol Values.** The daemon and the CLI always run the same mops version, so a protocol Value needs no compatibility
with other versions. Some protocol Values are also printed (`--json`); until mops 1.0, changing them may change the
public output.

### Logic

**`logic-no-io`**. Logic does no I/O. Everything it needs from the outside world arrives as parameters: Values, MPS
objects, functions, or a Kotlin `interface` that an Adapter implements (**`logic-interface`**).

**`logic-deterministic`**. Logic gives the same output for the same parameters.

**`logic-returns-values`**. Logic returns Values and has no other effect (**`entry-writes-results`**).

**`logic-stateless`**. Logic holds no mutable state between calls. Constructor parameters are configuration, fixed for
the life of the instance.

**`render-in-logic`**. Rendering (of Plans, findings, reports) that branches, loops, or is shared is Logic that returns
a string or a structured Value. Choosing between human-readable and JSON output is rendering, so it is Logic too. A
one-line string template may stay in the Entry point. Writing the result to stdout is the Entry point's job
(**`entry-writes-results`**).

**`logic-deps`**. Logic depends on Values, other Logic, Kotlin interfaces it defines itself, and the MPS types it reads.

**`logic-interface`**. When Logic needs something from the environment in the middle of its work, it takes a function
parameter (`hashFile: (Path) -> String`) or defines a Kotlin `interface` in its own package that an Adapter implements.
Prefer passing Values in; prefer a function parameter to an interface with one method.

### Adapter

**`adapter-one-thing`**. An Adapter translates between one external thing and Values: one file format, one tool, one
protocol.

**`adapter-translates-only`**. An Adapter is mechanism: it applies no policy beyond the translation itself. Choices that
the external format leaves open, and fallbacks between ways to read it, are translation. Other policy belongs in Logic.

**`adapter-owns-format`**. Everything mops knows about one external format stays in one class, or in classes in one
package that share a private representation. Reading and writing the format are done there. Code that encodes and
decodes the format without I/O is Logic that the Adapter uses (**`adapter-deps`**).

**`adapter-deps`**. Adapters depend on Values, the Kotlin interfaces they implement, external libraries, narrower
Adapters (an Ant runner may use a process launcher), and Logic about their own external thing (**`adapter-one-thing`**).
Adapter dependencies form no cycles. That Logic hides an Environment decision (Part 3), for example decoding the format
or deciding when a file's size and modification time prove that the file is unchanged. It lives in the Adapter's package
(**`adapter-owns-format`**). Logic that hides a Behavior decision, such as which MPS Modules to skip, stays outside the
Adapter (**`adapter-translates-only`**).

**`adapter-cache`**. An Adapter may keep a private cache: in-memory data that only saves work. The test: if the Adapter
dropped the cache before a call, that call would give the same results and behave the same for a user or client, only
slower. Examples: content hashes of files; what archives looked like when they were last refreshed, so that only changed
archives are refreshed again. Cache invalidation, deciding when cached data is still true, is Logic
(**`adapter-deps`**). **`state-one-owner`** applies to the cache in the same way as to State. Other mutable state is
State (**`state-justified`**).

### Entry point

**`entry-wires`**. An Entry point parses input, constructs the parts, calls them in order, and turns the result into
output and an exit code.

**`entry-delegates-policy`**. An Entry point applies only input and output policy, for example rejecting a missing
argument. Domain policy belongs in Logic (see Policy and mechanism). Move any `if` about MPS Modules, MPS Models or
dependencies from the Entry point into Logic.

**`entry-writes-results`**. Only Entry points write results (the output a user or client reads), and only by printing a
string or sending a Value that Logic produced (**`render-in-logic`**). Diagnostic log lines are different: Adapters and
Entry points may write them through a logger they receive.

**`entry-deps`**. Entry points may depend on anything. Only other Entry points depend on Entry points, for example
`Main` building the command tree or a command extending a base command.

### State

**`state-justified`**. State is a last resort. Each State class documents in its KDoc why a Value passed between calls
is not enough.

**`state-one-owner`**. Each State object has one owner. Share it across threads only when the class is thread-safe and
its KDoc says so.

## Part 2: Plan / Execute

A change that a user requests, and a query where choosing how to run it is policy, are divided into a Planner (Logic), a
Plan (Value) and an Executor (Adapter). [Plan / Execute](plan-execute.md) holds these rules (**`plan-split`**,
**`plan-complete`**, **`executor-follows-plan`**, **`dry-run-plans-only`**). Read it when you add or change such an
operation.

## Part 3: Information hiding

A **design decision** here is a choice that is likely to change. Divide code by the design decisions it hides, not by
processing steps.

### What a class hides

Each design decision falls into one of three categories:

| Category              | Meaning in mops                                   | Examples                                                                                        |
| --------------------- | ------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| **Environment**       | How an external thing works.                      | MPS descriptor formats, git commands, the filesystem layout of an MPS Project, launching a JVM. |
| **Behavior**          | What mops is required to do.                      | What `check` reports; when a dependency counts as missing.                                      |
| **Software decision** | Choices the developers made that users don't see. | The structure of the dependency graph; the algorithm for ordering MPS Modules.                  |

The category usually predicts the kind: Environment decisions live in Adapters and in the Logic they use
(**`adapter-deps`**), Behavior and Software decisions in Logic. A Value's shape is not hidden: it is the interface that
the classes using it share (**`connascence-in-types`**).

**`one-decision-per-class`**. Each class except a Value or an Entry point hides one design decision. If a class changes
for unrelated reasons, that is **Divergent Change**. Split it.

**`hides-line`**. Every class except a Value or an Entry point names the design decision it hides in a `Hides:` line in
its KDoc, for example `Hides: the XML layout of .msd module descriptors.`

### One design decision, one place

**`decision-in-one-place`**. Each design decision is hidden by exactly one class. If changing one design decision
requires editing several classes, that is **Shotgun Surgery**. Merge the classes, or move the design decision into one
of them. Classes share only the types they pass between them (**`connascence-in-types`**).

**`connascence-in-types`**. Two classes are **connascent** when a change to one forces a change to the other. Keep that
connascence in types both classes use: a Value, a sealed class, an enum or a Kotlin `interface`. Then the compiler finds
every place that must change. Connascence that the types do not show is **information leakage**. Examples:

- two classes build or parse the same string format;
- two classes map the same cases to the same literal strings or numbers;
- two classes depend on the same magic value;
- two classes depend on the order of calls.

Move that knowledge into a type both classes use. In particular:

- A closed set of values (an outcome, a status, a kind) is an enum or a sealed type, also on the wire. A set is closed
  when mops code branches on its values. `@SerialName` sets the wire spelling.
- When a type already knows something (serial names, field lists), derive it from the type instead of restating it.

### Depth

**`earns-its-place`**. Every class and method passes the **deletion test**: if you delete it, its complexity comes back
in its callers. A class that fails the test is a **Middle Man** (it only forwards calls) or a **Lazy Class** (it does
too little). Inline it into its callers or callees.

**`deep-classes`**. Prefer **deep** classes over **shallow** ones.

### Packages

**`package-by-decision`**. Organize packages by the design decision or feature they hide. Examples: one package both
reads and writes a descriptor format. The Planner, Plan and Executor of one operation share a package. Splitting by
processing stage is **temporal decomposition**. It spreads each design decision across several packages. Move code into
a package when a cluster of classes shares a design decision (for example all test-run classes), not one file at a time.
