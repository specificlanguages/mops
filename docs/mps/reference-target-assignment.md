# Assigning reference targets

In `com.jetbrains:mps:2025.1.2`, `mps-openapi.jar` exposes:

```java
void SNodeAccessUtil.setReferenceTarget(SNode node, SReferenceLink link, SNode target);
```

The target may be null to clear a reference. Use this API to assign a resolved target through MPS reference
setter hooks. An `SReference` supplies its resolved target through `getTargetNode()`; an `SNodeReference` can be
resolved with `resolve(SRepository)`. Resolution can return null. Callers that distinguish an unresolved target
from a request to clear must check resolution before invoking the setter.

`SNodeAccessUtilImpl` obtains the reference constraints descriptor, checks `validate`, assigns the target, and invokes
`onReferenceSet` when validation accepts it. A rejected validation leaves the reference unchanged. Missing reference constraints
descriptors and recursive setter calls use direct native assignment. This API does not constitute full model validation.

Verified against the MPS 2025.1 sources:

- `core/openapi/source/org/jetbrains/mps/openapi/model/SNodeAccessUtil.java`: public signature and dispatch.
- `core/kernel/source/jetbrains/mps/smodel/SNodeAccessUtilImpl.java`: setter behavior.
- `core/openapi/source/org/jetbrains/mps/openapi/model/SReference.java`: target resolution contract.
