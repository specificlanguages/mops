# Property handlers and child access

In `com.jetbrains:mps:2025.1.2`, `mps-openapi.jar` provides property access through `SNodeAccessUtil`:

```java
String getProperty(SNode node, SProperty property);
void setProperty(SNode node, SProperty property, String value);
Object getPropertyValue(SNode node, SProperty property);
void setPropertyValue(SNode node, SProperty property, Object value);
```

The string overloads are deprecated in favor of typed values, but invoke the same MPS property handlers.
They convert between serialized strings and the property's data type. The getter calls the property constraints
descriptor's `getValue`; the setter calls `setPropertyValue`. Recursive access from a handler falls back to direct
storage access. These hooks do not amount to full model validation.

`SNodeAccessUtil` has no child-access methods. MPS's `SLinkOperations` uses native `SNode.getChildren`, `addChild`,
and `removeChild` for containment operations. Those operations do not provide property-style getter/setter hooks.

Verified against the MPS 2025.1 sources:

- `core/openapi/source/org/jetbrains/mps/openapi/model/SNodeAccessUtil.java`
- `core/kernel/source/jetbrains/mps/smodel/SNodeAccessUtilImpl.java`
- `core/kernel/smodelRuntime/source_gen/jetbrains/mps/lang/smodel/generator/smodelAdapter/SLinkOperations.java`

The baseLanguage `ClassConcept.isStatic` handlers provide a concrete example: its getter reads the inverse of
`nonStatic`, and its setter writes the inverse to `nonStatic`. They are defined in
`languages/baseLanguage/baseLanguage/source_gen/jetbrains/mps/baseLanguage/constraints/ClassConcept_Constraints.java`.
