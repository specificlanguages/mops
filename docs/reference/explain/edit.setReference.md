# mops explain edit.setReference

Generated from `mops explain edit.setReference` by `./gradlew :cli:updateReference`. Do not edit this page. Back to the
[index](../index.md).

```text
edit.setReference — point or clear a node's reference.

FIELDS
  op       "setReference"
  target   (required) the node holding the reference — see target
  role     (required) the role of the reference link to set
  to       (optional) the node to point at; omit or null to clear the reference

SEMANTICS
  Sets the reference under role on the target node to point at the "to" node,
  which may live in another model. Omitting "to" (or null) clears the reference.

REFERENCE SETTER HOOKS
  Edit batches and Code Mode set references the way the MPS editor does, through
  SNodeAccessUtil.setReferenceTarget. A concept may define a referent set handler,
  which MPS runs with the old and the new target:

  - It may refuse the change. The reference then stays as it was, and the batch
    still succeeds, as in MPS.
  - It may change other parts of the node, even ones the batch gave explicitly.
    Setting overriddenMethod on a jetbrains.mps.lang.behavior method replaces the
    method's parameters, return type, and name.
  - An exception from the handler fails the batch, and nothing is changed. In Code
    Mode the exception reaches the script.

  Inline references ("references" in addChild, addRoot, replace, and wrap) go
  through the same hooks. They are set after the operation has put the new node
  and its children in place, so the handler sees the node as the batch built it,
  the same as a setReference operation that follows. The constraint check skips a
  reference that a handler refused or changed, and nodes that a handler removed.

EXAMPLE
{
  "operations": [
    {
      "op": "setReference",
      "target": {"model": "com.specificlanguages.json.structure", "nodeId": "2110045694544566904"},
      "role": "type",
      "to": "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/8007894750083424799"
    }
  ]
}

SEE ALSO
  edit, target, node-ref
```
