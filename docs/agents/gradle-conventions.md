# Gradle conventions

Use typesafe accessors for domain objects provided by plugins. E.g. `configurations.foo` instead of
`configurations.named("foo")`, same for `tasks`, extensions, etc.
