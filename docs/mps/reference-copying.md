# Copying native references

In `com.jetbrains:mps:2025.1.2`, `mps-openapi.jar` exposes:

```java
ResolveInfo SReference.describeTarget();
void SNode.setReference(SReferenceLink role, ResolveInfo target);
```

Copy a reference using `destination.setReference(role, sourceReference.describeTarget())`.
The target description is independent of the source node and role and preserves the reference's
resolution information without requiring target resolution.

The deprecated `SNode.setReference(SReferenceLink, SReference)` overload requires the reference's
source node and link to match the destination. It must not be used to copy a reference between nodes.

Verified against the MPS 2025.1 sources:

- `core/openapi/source/org/jetbrains/mps/openapi/model/SReference.java`: `describeTarget()` contract.
- `core/kernel/source/jetbrains/mps/smodel/SNode.java`: the `setReference` overloads and source/link assertions.

Custom reference implementations may leave `describeTarget()` unsupported; its default implementation throws
`UnsupportedOperationException`.
