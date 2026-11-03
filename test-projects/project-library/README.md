# Project-library test fixture

The project's only module is the test solution `mops.librarytests`. Its language test uses a class from `mops.library`,
a solution that the project loads from the project library in `.mps/libraries.xml` (`libs/`), not from
`.mps/modules.xml`.

The model also has a `TestInfo` root whose project path is `${mops.fixture.project}`. The test opens the project at that
path, so it passes only when the test run defines the `mops.fixture.project` path macro.
