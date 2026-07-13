# Setting property values: SNodeAccessUtil.setProperty vs setPropertyValue

Verified against the MPS 2025.1.2 distribution jars (`com.jetbrains:mps:2025.1.2`). Bytecode read from
`jetbrains.mps.smodel.SNodeAccessUtilImpl` in `mps-core.jar`; the public surface is
`org.jetbrains.mps.openapi.model.SNodeAccessUtil` in `mps-openapi.jar`.

## The two setters are not interchangeable

`SNodeAccessUtil` exposes two overloaded setters that look alike but differ in what they expect for the value:

```
static void setProperty(SNode node, SProperty property, String value)   // persisted-form string
static void setPropertyValue(SNode node, SProperty property, Object value)  // already-typed value
```

- **`setProperty(node, property, String)`** converts the string with the property's datatype first:
  `property.getType().fromString(value)` produces the typed value (a `Boolean` for a boolean property, an `Integer`
  for an integer property, the string itself for a string property), then stores it via `setPropertyValueImpl`.
- **`setPropertyValue(node, property, Object)`** stores the object as-is (through the property's
  `PropertyConstraintsDescriptor`), performing **no** string conversion. It expects the already-typed value.

In Kotlin/Java a `String` argument binds to the `Object` overload, so `setPropertyValue(node, property, "true")`
compiles and runs but hands a `String` to a datatype that expects a `Boolean`.

## Why the wrong overload silently loses non-string values

Reads are datatype-symmetric with `setProperty`, not with `setPropertyValue`:

```
getProperty(node, property)  ==  property.getType().toString( getPropertyValueImpl(node, property) )
```

- A **string** property survives either setter: its `fromString`/`toString` are identity, so a raw `String` passed to
  `setPropertyValue` happens to be the correct typed value already.
- A **boolean** property (e.g. `jetbrains.mps.lang.structure.structure.ConceptDeclaration.rootable`, persisted role
  `19KtqR`) does not: `setPropertyValue(node, prop, "true")` stores a `String` where the boolean descriptor expects a
  `Boolean`, the value is dropped, and `getProperty` / `node.getProperty` return `null` afterwards — the property reads
  back as unset. The same hazard applies to any non-string datatype (integer, enum).

## Guidance

- When the value is in **persisted form** (a string from JSON, a file, or CLI input), use
  **`setProperty(node, property, String)`**. It round-trips every datatype because it mirrors the `getProperty` read
  path.
- Use **`setPropertyValue(node, property, Object)`** only when you genuinely hold the already-typed value (an actual
  `Boolean`, `Integer`, etc.).
- **Clearing:** a `null` value removes the property. `setPropertyValue(node, property, null)` clears directly.
  `SNode.getProperty` returning `null` means "unset" — for a boolean that is indistinguishable from `false`, since MPS
  omits the default value from persistence.
