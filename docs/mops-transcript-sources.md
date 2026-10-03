# Mops example sources

The [bundled recipes](../skills/mops/references/README.md) are grounded in the current CLI, help catalog, tests, and
local implementation transcripts. The local research scan covered 856 Codex transcript files and found 92
command-bearing chats. The private transcript inventory is kept outside the published documentation.

Implementation workflows informed recipes for copying existing regression tests, inserting Java as BaseLanguage,
retargeting references between stub models, maintaining explicit model imports, and refreshing build-model modules. The
current code and executable tests determine the supported syntax.

- [CLI command registration](../cli/src/main/kotlin/com/specificlanguages/mops/cli/Main.kt)
- [Code Mode API catalog](../daemon/src/main/kotlin/com/specificlanguages/mops/daemon/CodeCatalog.kt)
- [Property semantics](../daemon/src/test/kotlin/com/specificlanguages/mops/daemon/CodeModePropertiesTest.kt)
- [Children and reference semantics](../daemon/src/test/kotlin/com/specificlanguages/mops/daemon/CodeModeLinksTest.kt)
- [Traversal semantics](../daemon/src/test/kotlin/com/specificlanguages/mops/daemon/CodeModeTraversalTest.kt)
- [Java parser examples](../daemon/src/test/kotlin/com/specificlanguages/mops/daemon/JavaSnippetParserTest.kt)
- [Executable recipe checks](../cli/src/integrationTest/kotlin/com/specificlanguages/mops/cli/BundledExamplesIntegrationTest.kt)
- [Offline CLI and JSON checks](../cli/src/test/kotlin/com/specificlanguages/mops/cli/ExamplesCommandTest.kt)
