# mops explain edit.setProperty

Generated from `mops explain edit.setProperty` by `./gradlew :cli:updateReference`. Do not edit this page. Back to the
[index](../index.md).

```text
edit.setProperty — set or clear a single property on a node.

FIELDS
  op       "setProperty"
  target   (required) the node to modify — see target
  name     (required) the property name
  value    (optional) the new string value; omit or null to clear the property

SEMANTICS
  Writes the named property on the target node. Omitting value (or passing null)
  clears the property back to its empty state.

  For an enumeration property, value is a literal name (for example "_0__1"), or a
  presentation ("0..1") that only one literal has. Any other value fails the batch
  with INVALID_PROPERTY_VALUE and lists the literals. Reads report the literal name.

EXAMPLE
{
  "operations": [
    {
      "op": "setProperty",
      "target": "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904",
      "name": "name",
      "value": "RenamedJsonFile"
    }
  ]
}

SEE ALSO
  edit, target, node-ref
```
