<!-- Generated from examples/pages and examples/specs; run :cli:updateExamples. -->

# Create modules and models

| Task                                  | Command                                                                      |
| ------------------------------------- | ---------------------------------------------------------------------------- |
| Create a language with a generator    | `mops create language sample.language --with-generator`                      |
| Create a Java solution                | `mops create solution sample.solution --usage-preset java`                   |
| Create a test solution                | `mops create solution sample.tests --usage-preset java-tests`                |
| Create a devkit                       | `mops create devkit sample.devkit`                                           |
| Create an embedded generator          | `mops create generator --language sample.language --alias main`              |
| Create a standalone generator         | `mops create generator --language sample.language --alias main --standalone` |
| Create a model relative to a module   | `mops create model .main --module sample.solution`                           |
| Create a model with one file per root | `mops create model .main --module sample.solution --file-per-root`           |
| Preview creation                      | `mops create solution sample.solution --usage-preset java --dry-run`         |

Solution presets configure facets, not dependencies, imported languages, devkits, or contents. Supported presets:
`not-generated` (default), `text`, `java`, `java-tests`, and `java-mps-plugin`.
