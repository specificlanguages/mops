<!-- Generated from examples/pages and examples/specs; run :cli:updateExamples. -->

# Setup and discover the API

| Task                                                   | Command                                     |
| ------------------------------------------------------ | ------------------------------------------- |
| Discover Gradle-configured MPS/Java and write wrappers | `mops wrapper`                              |
| Write a wrapper for one project                        | `mops wrapper --project-root ../my-project` |
| Select a project explicitly                            | `mops --project-root ../my-project list`    |
| Supply an MPS distribution directly                    | `mops --mps-home /path/to/MPS list`         |
| Discover CLI commands                                  | `mops --help`                               |
| Discover a command's options                           | `mops create solution --help`               |
| Discover Groovy helpers                                | `mops code help`                            |
| Inspect helper signatures and access requirements      | `mops code help mops.parsing.java`          |
| Inspect native node members and extensions             | `mops code help SNode`                      |
| Read edit notation                                     | `mops explain edit`                         |
| Read search scope syntax                               | `mops explain scope`                        |
| Locate bundled agent guidance                          | `mops skill path`                           |

Global options may appear before, between, or after command names. Command-specific options belong after their command.
Wrappers discover configured distributions and can download them, but do not execute preparation or language build
tasks.
