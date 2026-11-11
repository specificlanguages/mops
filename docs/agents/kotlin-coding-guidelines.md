# Kotlin Coding Guidelines

Project-specific Kotlin conventions that are easy to miss from code alone. Prefer nearby code style for ordinary Kotlin
formatting and naming.

- Prefer one top-level class, object, interface, or standalone data class per Kotlin file.
- Keep related sealed request types together in one file and related sealed response types together in one file.
- Use a camel-case name for a Kotlin file that does not contain a single top-level class/object/interface/data class.
  Example: `daemonProtocol.kt` instead of `DaemonProtocol.kt`
- Keep class names descriptive. If the purpose of a class has changed and the class name does not adequately describe it
  anymore, rename the class.
- In tests, when expected values cover a data object's properties, compare the complete data object instead of asserting
  every property separately. This keeps the assertion aligned with the object's equality contract and catches newly
  added properties.
- Wildcard imports are acceptable.
- Get the serializer of a `@Serializable` class declared in the same module with `serializer<X>()`, not
  `X.serializer()`. detekt's type resolution does not run the serialization compiler plugin, so it cannot see the
  generated `X.serializer()` and reports a compiler error. The plugin compiles `serializer<X>()` to the same direct
  `X.Companion.serializer()` call.
- Keep mutable state owned by one class. An inner class may read the state of its outer class but does not change it.
  Return what changed and let the outer class apply it. The detekt rule `InnerClassChangesOuterState` enforces this for
  `inner` classes.
- Keep each decision in one place. When a reader asks "when does X happen?", one function should answer it. If the
  answer needs two functions in different objects, move the rule into one of them.
- Make the role of a class clear from its name. A class is a value (immutable data), a service (does work, named with a
  verb or `-er`), or a stateful policy (remembers history to decide). If a class needs a second role, give that role its
  own class.
