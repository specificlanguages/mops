# JSON node export is synthesized from MPS model state

mops will synthesize JSON node exports from MPS model state available through the daemon. The export is a read-only view
of MPS model state, not a translation of MPS XML persistence. This keeps `get-node` on the same semantic foundation as
later model queries and edits.

JSON is the user-facing format so the export is not mistaken for MPS XML persistence. The format uses names for language
vocabulary, such as concepts, properties, references, and containment roles, while keeping serialized model references
and node IDs for model instance identity.

## Consequences

The export should expose what MPS exposes from MPS repository state. It should not read persistence files, preserve
XML import indices, emit persistence `resolve` text, or promise round-tripping to `.mps` files.

Compact regular node IDs may be accepted as command input for convenience, but output uses serialized MPS node IDs and
model references. Future edit/query operations must resolve language names from their structural context and fail on zero
or multiple matches instead of guessing.
