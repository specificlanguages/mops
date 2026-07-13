# Edit Plugins are mops-native; MPS-registered editing behaviors are not invoked

> **Status: PLANNED — NOT IMPLEMENTED.** This ADR records a design decision that has not been built. There is no
> `mops-plugin-api` artifact, no plugin dispatch in the write-executor, and no `hints` field in the edit response.
> The design below stands as the intended direction, not a description of current behavior.

When an **Edit Operation** **Materializes** nodes — creates them fresh or as copies — mops dispatches **Edit
Plugins**: JVM implementations of a mops-owned SPI published as `mops-plugin-api` (the SPI interface and context
object are mops's stability surface; MPS Open API types like `SNode` are used directly, `compileOnly`). A plugin
declares the concepts it applies to; mops walks the whole materialized subtree and invokes the plugin on every
**Concept Instance** of a declared concept — subconcept-inclusive, at any depth, with no short-circuit, because MPS
models freely mix instances from different languages in one subtree and one plugin's match must never hide a node
from another plugin. Dispatch runs inside the same write action, immediately after the operation and before later
operations in the batch, so dependent operations observe post-plugin state and rollback reverts plugin writes like
any other. Plugins disclose what they did through **Hints**; mops itself emits nothing.

mops deliberately does **not** invoke the behaviors languages register with MPS for IDE editing — paste
post-processors, copy pre-processors, node factories — even though `DataTransferManager.postProcessNode` sits on the
daemon classpath and would reproduce IDE paste for any loaded language for free. The structure language's meta-id
handling (reassigning `conceptId` on copy) therefore ships as a bundled Edit Plugin through the same front door every
other language would use.

## Considered Options

- **Invoke MPS-registered paste post-processors via `DataTransferManager`** — rejected: the API is `void` and opaque
  (no way to learn what fired, so no honest **Hints**), dispatch is exact-concept (a cache quirk, not a semantic),
  its threading and language-loading requirements are unverified runtime contracts, and it imports unaudited
  IDE-oriented behavior from every loaded language. Evidence in `docs/mps/node-creation-factories-and-paste.md`.
- **Node factories (`NodeFactoryManager.setupNode`) to fix up copies** — rejected: factories assume a blank node and
  clobber real data; the structure language's own factory unconditionally resets a concept's `extends`.
- **Hardcode `conceptId` handling in the write-executor** — rejected: solves one built-in language and leaves every
  user DSL unserved.
- **JavaScript plugins on the Edit Script facade (ADR-0006)** — rejected for now: Edit Scripts are not built, and
  the plugin mechanism must not wait for them.
- **MPS-aspect-discovered plugins** (a `mops`/`hooks` aspect on a language) — deferred: a possible future front end
  that would feed the same SPI, not a replacement for it.

## Consequences

mops copy knowingly diverges from IDE paste for languages that rely on MPS-registered handlers; coverage is
plugin-by-plugin. Dispatch semantics are mops-owned and documented rather than inherited: subconcept-inclusive
matching, every matching node, deterministic order, plugins handle single nodes with no subtree ownership, and a
**Move Leaf** never triggers dispatch because it materializes nothing. Plugin effects are ordinary transactional
writes: **Constraint** evaluation checks state, indifferent to whether a plugin or an operation produced it. There is
no per-operation opt-out — an explicit `setProperty` later in the same batch overrides a plugin's write because it
runs later. v1 discovers only bundled plugins via `ServiceLoader` on the daemon classpath; loading project-local
plugin jars is a follow-up that must solve daemon-reuse staleness (the `DaemonContext` compatibility check must
fingerprint plugin jars) and state a trust posture before it ships.
