# Resolving MPS concepts by name

**Question this answers:** given a concept's name as a string, how do you get the `SAbstractConcept` for it? What name
forms does the platform's built-in lookup accept, what does it return on a miss, and how do you instead look a concept
up by its **short** name across all loaded languages while detecting ambiguity (the same short name in more than one
language)?

**Answer:** MPS has exactly one built-in string-to-concept lookup, `ConceptRegistry.getConceptByName`, and it matches
the **fully-qualified** name only (`<language>.structure.<ConceptName>`). It is deprecated, returns a non-null
placeholder on a miss, and offers no short-name or ambiguity handling. To resolve a short name you build the index
yourself by iterating `LanguageRegistry.getAllLanguages()` → `SLanguage.getConcepts()` and comparing
`SAbstractConcept.getName()`; ambiguity is something only your own index can see, because the platform's index is keyed
by qualified name and silently overwrites nothing (qualified names are unique).

Verified against MPS **2025.1.2** (`com.jetbrains:mps:2025.1.2`), cross-checked in the source checkouts for
**2022.2**, **2024.1**, **2025.1**, and tip. Source paths below are relative to the
[JetBrains/MPS](https://github.com/JetBrains/MPS) repository root.

## The name model

A concept carries three name-related accessors, all on `org.jetbrains.mps.openapi.language.SAbstractConcept`
(`mps-openapi.jar`; source `core/openapi/source/org/jetbrains/mps/openapi/language/SAbstractConcept.java`):

| Method               | Returns                                             | Notes |
|----------------------|-----------------------------------------------------|-------|
| `getName()`          | the **short** concept name, e.g. `ClassConcept`     | `@NotNull`. This is the short name to match against. |
| `getQualifiedName()` | `<language>.structure.<Name>`, e.g. `…structure.ClassConcept` | **`@Deprecated`** (no replacement given). Still the key the built-in lookup uses. |
| `getLanguage()`      | the owning `SLanguage`                              | `@NotNull`. `getLanguage().getQualifiedName()` is the language namespace (the qualified name minus `.structure.<Name>`). |
| `isValid()`          | `true` iff the concept is fully functional          | Returns `false` when the containing language is absent/unloaded — this is how you tell a real hit from the miss placeholder. |

The qualified name always embeds a literal `.structure.` infix before the short name, because a language's structure
model is named `<language>.structure`. Note the edge case that the language namespace can *itself* end in `.structure`
(e.g. `jetbrains.mps.lang.structure`), so its concepts are qualified `jetbrains.mps.lang.structure.structure.<Name>`
— splitting on the *last* `.structure.` recovers the language.

That `getQualifiedName()` is deprecated is the important trend: MPS identifies concepts by **id** (`SConceptId`:
language UUID + concept id), not by name. Name is a lookup convenience for legacy persistence and human input, and both
the by-name lookup and the qualified-name string are on the way out. Any code that resolves by name is opting into a
deprecated-but-durable corner (see the "compatibility" comment on the method below).

## The built-in lookup: `ConceptRegistry.getConceptByName`

```
jetbrains.mps.smodel.language.ConceptRegistry        // mps-core.jar
  @Deprecated(since = "3.4", forRemoval = true)
  synchronized SAbstractConcept getConceptByName(String conceptName)
```

Source: `core/kernel/source/jetbrains/mps/smodel/language/ConceptRegistry.java`. Obtain the registry with
`ConceptRegistry.getInstance()`. The whole method body, unchanged 2022.2 → 2025.1:

```java
synchronized public SAbstractConcept getConceptByName(String conceptName) {
  if (myConceptByNameCache == null) {
    myConceptByNameCache = new HashMap<>();
    for (SLanguage l : myLanguageRegistry.getAllLanguages()) {
      for (SAbstractConcept c : l.getConcepts()) {
        myConceptByNameCache.put(c.getQualifiedName(), c);   // keyed by QUALIFIED name
      }
    }
  }
  return myConceptByNameCache.getOrDefault(conceptName, new InvalidConcept(conceptName));
}
```

Behavior contract:

- **Qualified name only.** The cache is keyed by `getQualifiedName()`, so only the full
  `<language>.structure.<Name>` form hits. A bare short name, or the qualified name with the `.structure.` infix
  dropped, misses.
- **Never returns null.** A miss yields an `InvalidConcept` (`jetbrains.mps.smodel.adapter.structure.concept.InvalidConcept`,
  `mps-core.jar`), a placeholder whose `isValid()` returns `false` and whose `getQualifiedName()` echoes the string you
  passed in. **Always gate the result on `isValid()`** — do not null-check.
- **Cached, invalidated on language load.** The map is built once and cleared in `afterLanguagesLoaded` (via
  `clearConceptsCache()`). A concept whose language loads *after* the first call is picked up because the cache was
  dropped; but within a stable language set the cost is a one-time scan.
- **Deprecated since "3.4"** (an MPS 3.x / ~2016 platform version), `forRemoval = true`. The in-source comment says it
  survives "for compatibility purposes… provided we have support for legacy persistence that needs by-name concepts,"
  i.e. it is not going away soon but you are on notice.
- **Must run with languages available.** `getConcepts()` (below) only returns anything once the language's structure
  aspect is loaded, so run name resolution inside a read action with the relevant languages loaded.

There is **no** `SConceptRepository` class in the platform (a plausible-sounding name, but it does not exist). The
registry above plus the `LanguageRegistry` are the concept-lookup surface.

## Rolling your own: short name + cross-language ambiguity

The built-in cannot answer "which concept(s) are named `Foo`?" because it is keyed by unique qualified names. Build the
index yourself over the same two APIs the registry uses:

```
jetbrains.mps.smodel.language.LanguageRegistry       // mps-core.jar
  Collection<SLanguage> getAllLanguages()
org.jetbrains.mps.openapi.language.SLanguage         // mps-openapi.jar
  Iterable<SAbstractConcept> getConcepts()
```

Get the registry from the project: `project.getComponent(LanguageRegistry.class)`. Then, inside a read action:

```kotlin
val matches = languageRegistry.getAllLanguages()
    .flatMap { it.getConcepts() }          // SConcept + SInterfaceConcept, as SAbstractConcept
    .filter { it.name == shortName }        // getName() is the short name
    .distinctBy { it.getQualifiedName() }
// matches.size == 0 -> not found; == 1 -> unique; > 1 -> ambiguous, report each getQualifiedName()
```

Notes and gotchas:

- **`getConcepts()` returns both concepts and interface concepts** — the element type is `SAbstractConcept`
  (source `core/kernel/…/adapter/structure/language/SLanguageAdapter.java`, `getConcepts()`, which emits a
  `SConceptAdapterById` or `SInterfaceConceptAdapterById` per structure descriptor). Filter by
  `is SConcept` / `is SInterfaceConcept` if you want only one kind.
- **Empty for an unloaded/invalid language.** `getConcepts()` fetches the language's `StructureAspectDescriptor` from
  its runtime; if the language runtime or its structure aspect is not loaded, it returns an empty list — not an error.
  So a language that is "present in the project" but whose structure is not built contributes zero concepts, and a short
  name defined only there will look "not found." Distinguish "no such name anywhere" from "its language isn't loaded"
  before reporting, or the diagnosis misleads. (The javadoc phrases this as "empty if the language is invalid
  (missing).")
- **Ambiguity is real and expected.** The same short name legitimately occurs in multiple languages (common concept
  names like `Statement`, `Type`, `Expression`). Counting matches — not guessing by shape — is the only correct
  resolution; report every qualified candidate on a tie.
- **Cost.** This scans every concept of every loaded language on each call. Cache it yourself (and invalidate on
  language load/unload, as `ConceptRegistry` does) if you resolve names hot.

## Identifying by id (the non-deprecated path, for completeness)

When you already hold a concept's ids, resolve it by id rather than name — this is the API MPS steers toward:

```
jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory     // mps-core.jar
  static SConcept getConcept(SLanguage lang, long conceptId, String nameHint)
  static SConcept getConcept(long langHi, long langLo, long conceptId, String nameHint)
  static SConcept getConcept(SConceptId id, String nameHint)
  // …and getInterfaceConcept(…) equivalents
```

The `String` argument is only a name **hint** for diagnostics; identity is the numeric id. This does not help
name-based lookup (you must already know the id), but it is why by-name lookup is deprecated: names are not the
concept's identity. Do not try to synthesize ids from a name.

## Version drift

| Aspect | 2022.2 → 2025.1 | tip |
|--------|-----------------|-----|
| `ConceptRegistry.getConceptByName` | identical body, keyed by qualified name, `InvalidConcept` on miss | same contract; body refactored to `myLanguageRegistry.withAvailableLanguages(lr -> for (c : lr.getConcepts()) …)` iterating `LanguageRuntime` instead of `SLanguage` — result unchanged |
| `@Deprecated(since = "3.4", forRemoval = true)` on the method | present | present |
| `SAbstractConcept.getName()` / `getQualifiedName()` / `getLanguage()` / `isValid()` | present, same signatures; `getQualifiedName()` `@Deprecated` | present |
| `LanguageRegistry.getAllLanguages(): Collection<SLanguage>` | present | present (plus `withAvailableLanguages(Consumer<LanguageRuntime>)` used internally) |
| `SLanguage.getConcepts()` | present, returns empty for invalid language | present |

The stable, cross-version surface for name resolution is therefore: `ConceptRegistry.getInstance().getConceptByName`
for qualified names, and `LanguageRegistry.getAllLanguages()` + `SLanguage.getConcepts()` + `getName()` for anything
else. Both have held from 2022.2 to tip.

## Re-verify triggers

- On an MPS upgrade, confirm `getConceptByName` still returns `InvalidConcept` (not `null`) on a miss and is still
  keyed by qualified name — the `forRemoval = true` flag means it could one day disappear, at which point the
  hand-rolled iteration becomes the only path.
- If `SAbstractConcept.getQualifiedName()` (already deprecated) is removed, replace qualified-name keys/reporting with
  `getLanguage().getQualifiedName() + ".structure." + getName()` or with id-based identity.
- If you start seeing "not found" for names you expect, check language loading first (`getConcepts()` empty for an
  unloaded language) before suspecting the lookup.
