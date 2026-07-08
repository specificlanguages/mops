# Verified MPS API notes

Contracts of MPS internal APIs that mops relies on, confirmed against the MPS jars and source checkout (not guessed
from memory). One file per API or topic.

Each note records: the exact class and signature, which jar ships it, the source file the behavior was read from
(with the MPS version), the behavior contract, and the gotchas that make the API easy to misuse. When mops moves to a
new MPS version, re-verify the notes that back code you are touching.
