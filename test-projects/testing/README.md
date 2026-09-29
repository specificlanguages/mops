# Test-running fixtures

This project contains native BaseLanguage tests, a language `NodesTestCase`, a generator transformation assertion,
ordinary Jupiter tests, a parameterized test, and a legacy JUnit 3 test. Passing, failing, skipped, assumption-aborted,
and failing-container cases are intentional.

`slow.mps` completes one method before sleeping, and `crash.mps` completes one method before halting the worker JVM. The
integration suite runs these separately to verify interruption and partial-report persistence. The full-project
selection test removes those two models from its temporary copy before running the rest.

The generator test transforms an empty model and compares its output with the same model. It exercises generator fixture
setup, execution, and reporting without depending on a project language's transformation behavior.

The persisted Jupiter API imports use the `org.junit.junit5` module from MPS 2024.1–2026.1. For MPS 2026.2 EAP1, the
integration suite changes those imports to `JUnit` in the temporary project copy, matching that distribution's stub
layout. Parameterized-test imports remain in `org.junit.junit5`.
