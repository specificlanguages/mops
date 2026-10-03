# Setup and discover the API

| Task                                                   | Command          |
| ------------------------------------------------------ | ---------------- |
| Discover Gradle-configured MPS/Java and write wrappers | `{{example-1}}`  |
| Write a wrapper for one project                        | `{{example-2}}`  |
| Select a project explicitly                            | `{{example-3}}`  |
| Supply an MPS distribution directly                    | `{{example-4}}`  |
| Discover CLI commands                                  | `{{example-5}}`  |
| Discover a command's options                           | `{{example-6}}`  |
| Discover Groovy helpers                                | `{{example-7}}`  |
| Inspect helper signatures and access requirements      | `{{example-8}}`  |
| Inspect native node members and extensions             | `{{example-9}}`  |
| Read edit notation                                     | `{{example-10}}` |
| Read search scope syntax                               | `{{example-11}}` |
| Locate bundled agent guidance                          | `{{example-12}}` |

Global options may appear before, between, or after command names. Command-specific options belong after their command.
Wrappers discover configured distributions and can download them, but do not execute preparation or language build
tasks.
