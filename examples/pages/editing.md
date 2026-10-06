# Editing: choose an approach

Start with navigation and search to obtain stable references. Replace the uppercase placeholders in examples with
references copied from mops output.

{{table:approaches|Read}}

Use JSON batches for explicit edit operations with constraint diagnostics; use Code Mode for traversal and custom logic.
Resolve and validate targets before mutations. Code Mode commands save on success, but do not roll back partial edits on
an exception. A constraint check does not replace a model check, build, or regression test.
