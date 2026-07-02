# Model Edit Batch

`mops model edit` reads a JSON batch from stdin or `--file` and applies it through the daemon.
The current schema covers the `setProperty` operation only. We will extend the schema as new edit
operations land.

Schema: [docs/model-edit.schema.json](model-edit.schema.json)

## Examples

Set a property by serialized node reference:

```json
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
```

Clear a property by model target plus node id:

```json
{
  "operations": [
    {
      "op": "setProperty",
      "target": {
        "model": "/absolute/path/to/model.mps",
        "nodeId": "2110045694544566904"
      },
      "name": "conceptAlias"
    }
  ]
}
```

Read from a file:

```sh
mops --mps-home /path/to/mps model edit --file edit-batch.json
```

## Notes

- `value` omitted or `null` clears the property.
- `target` currently accepts a node reference string or a model target plus node id.
- The schema and examples will evolve as `mops model edit` gains more operation kinds.
