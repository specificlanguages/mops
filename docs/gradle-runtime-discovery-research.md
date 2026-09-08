# Discovering MPS and Java through Gradle

## Recommendation

The CLI implements `mops guess-command-line [PATH]` using a bundled Gradle init script. It reads `mpsDefaults` for
Specific Languages 2.x and conventional `RunAntScript` arguments and executable settings for mbeddr projects.
It selects a complete usable pair from the closest enclosing Gradle project, then a deterministic first match,
and preserves partial discoveries when no complete pair is available. It reports the selected source and
POSIX-shell-quoted arguments without starting a daemon or requiring preconfigured homes.

The probe uses the build's wrapper, disables configuration caching and configuration on demand, and queries providers
in an isolated report task. Provider evaluation may download or extract runtimes; preparation and language build task
actions are not executed. Absolute Java executable strings and files are supported for mbeddr; unsupported values
produce a diagnostic. Selectors, automatic daemon-startup discovery, persistent configuration, and Specific Languages
1.9 compatibility are outside the implementation scope.

Run `./gradlew :cli:test :cli:gradleDiscoveryTest` for CLI unit tests and live wrapper/plugin fixture validation.
The detailed edge cases below record API research and possible extensions, not additional supported behavior.

## Sources and version scope

The findings were checked against these upstream sources:

- [Specific Languages Gradle plugin](https://github.com/specificlanguages/mps-gradle-plugin/tree/249b3f55ec3bd8e7ebb39ecb31de85e0e8f6c2ad), whose MPS plugin declares version 2.1.0.
- [mbeddr MPS Gradle plugin](https://github.com/mbeddr/mps-gradle-plugin/tree/649c88d39d82711cb85e4093b096e29fe2027529).
- [Specific Languages 1.9.0 source](https://github.com/specificlanguages/mps-gradle-plugin/tree/4cc2e893df18d155e190305c00e58b01e2867bc8).

The source links below refer to those revisions. Version coverage beyond them is not established by this investigation.

## Specific Languages plugin

The plugin registers the extension as **`mpsDefaults`**. Its interface KDoc incorrectly calls it `mps`; the registration is authoritative. The extension exposes `mpsHome: DirectoryProperty` and `javaLauncher: Property<JavaLauncher>`. Conceptually, a Groovy probe reads:

```groovy
def defaults = project.extensions.findByName('mpsDefaults')
def mpsHome = defaults.mpsHome.get().asFile
def launcher = defaults.javaLauncher.get()
def javaHome = launcher.metadata.installationPath.asFile
def javaExecutable = launcher.executablePath.asFile
```

Use the configured launcher, because users can override the JBR convention with another Java installation. Name the result `javaHome`, with JBR identification as metadata if available. Do not label every launcher as JBR. Sources: [extension properties](https://github.com/specificlanguages/mps-gradle-plugin/blob/249b3f55ec3bd8e7ebb39ecb31de85e0e8f6c2ad/subprojects/mps-gradle-plugin/src/main/kotlin/com/specificlanguages/mps/MpsDefaultsExtension.kt#L9), [extension registration and conventions](https://github.com/specificlanguages/mps-gradle-plugin/blob/249b3f55ec3bd8e7ebb39ecb31de85e0e8f6c2ad/subprojects/mps-gradle-plugin/src/main/kotlin/com/specificlanguages/mps/MpsPlugin.kt#L229).

The default MPS provider resolves the `mps` configuration and extracts its distribution into `MpsPlatformCache`. The default Java launcher comes from `jbrToolchain`, whose provider resolves and extracts the `jbr` configuration before constructing a toolchain specification. These operations happen when providers are queried; they do not require running `setup`. Querying a path can therefore download archives, extract them, and inspect Java. Even an offline Gradle invocation can extract cached archives. Sources: [cache resolution and extraction](https://github.com/specificlanguages/mps-gradle-plugin/blob/249b3f55ec3bd8e7ebb39ecb31de85e0e8f6c2ad/subprojects/mps-platform-cache/src/main/kotlin/com/specificlanguages/mpsplatformcache/MpsPlatformCache.kt#L33), [JBR provider](https://github.com/specificlanguages/mps-gradle-plugin/blob/249b3f55ec3bd8e7ebb39ecb31de85e0e8f6c2ad/subprojects/jbr-toolchain/src/main/kotlin/com/specificlanguages/jbrtoolchain/JbrToolchainPlugin.kt#L40), [launcher provider](https://github.com/specificlanguages/mps-gradle-plugin/blob/249b3f55ec3bd8e7ebb39ecb31de85e0e8f6c2ad/subprojects/jbr-toolchain/src/main/kotlin/com/specificlanguages/jbrtoolchain/JbrToolchainExtension.kt#L10).

Do not derive cache paths from artifact names. The cache permits a custom root and handles transitive marker dependencies by examining the resolved archive. Java home differs from the extracted JBR root on macOS: the plugin appends `Contents/Home`. Read launcher metadata instead. Sources: [cache implementation](https://github.com/specificlanguages/mps-gradle-plugin/blob/249b3f55ec3bd8e7ebb39ecb31de85e0e8f6c2ad/subprojects/mps-platform-cache/src/main/kotlin/com/specificlanguages/mpsplatformcache/MpsPlatformCache.kt#L29), [OS normalization](https://github.com/specificlanguages/mps-gradle-plugin/blob/249b3f55ec3bd8e7ebb39ecb31de85e0e8f6c2ad/subprojects/jbr-toolchain/src/main/kotlin/com/specificlanguages/jbrtoolchain/internal/JbrOsArch.kt#L8).

Extension values are project defaults, not a guarantee about every task. `RunAnt` tasks receive `javaLauncher` and `pathProperties['mps_home']`/`['mps.home']` from the extension, but each can be overridden. Task-specific discovery should read those task properties, and detect conflicting MPS property values. `valueProperties` and `options` can introduce additional Ant properties; a complete task adapter must inspect these for conflicting overrides too. Sources: [task defaults](https://github.com/specificlanguages/mps-gradle-plugin/blob/249b3f55ec3bd8e7ebb39ecb31de85e0e8f6c2ad/subprojects/mps-gradle-plugin/src/main/kotlin/com/specificlanguages/mps/MpsPlugin.kt#L261), [RunAnt properties and argument construction](https://github.com/specificlanguages/mps-gradle-plugin/blob/249b3f55ec3bd8e7ebb39ecb31de85e0e8f6c2ad/subprojects/mps-gradle-plugin/src/main/kotlin/com/specificlanguages/mps/RunAnt.kt#L45).

### Older versions

Version 1.9.0 has the same extension name and `mpsHome`, but additionally has `javaExecutable: RegularFileProperty`, which takes precedence over `javaLauncher`. Its default launcher selects the Gradle build's toolchain; JBR is not automatically applied. Its MPS provider uses artifact transforms. The historical files are `subprojects/mps-gradle-plugin/src/main/kotlin/com/specificlanguages/{MpsDefaultsExtension,MpsPlugin}.kt` in the [1.9.0 source](https://github.com/specificlanguages/mps-gradle-plugin/tree/4cc2e893df18d155e190305c00e58b01e2867bc8).

The changelog records `javaExecutable` introduced in 1.8, `javaLauncher` introduced in 1.9, and `javaExecutable` removed in 2.0. Version 2.0 also moves classes and replaces `com.specificlanguages.RunAntScript` with `com.specificlanguages.mps.RunAnt`. This favors capability checks on the extension over a dependency on its Java class. The implementation targets only 2.x; the 1.x APIs are historical reference and are not supported. Source: [plugin changelog](https://github.com/specificlanguages/mps-gradle-plugin/blob/249b3f55ec3bd8e7ebb39ecb31de85e0e8f6c2ad/subprojects/mps-gradle-plugin/CHANGELOG.md#L37).

## mbeddr plugin

### RunAntScript

`de.itemis.mps.gradle.RunAntScript`, including its `BuildLanguages` and `TestLanguages` subclasses, does not expose an MPS home. Its relevant configuration is:

- `scriptArgs: List<String>`.
- `includeDefaultArgs`, default `true`.
- `executable: Any?`.
- Project properties `itemis.mps.gradle.ant.defaultScriptArgs` and `itemis.mps.gradle.ant.defaultJavaExecutable`.

The action copies task `scriptArgs`, then appends project defaults if enabled. These are Ant command-line arguments, despite the documentation describing JVM arguments. Java selection uses `task.executable` when non-null, otherwise `project.findProperty('itemis.mps.gradle.ant.defaultJavaExecutable')`; neither means a known JBR. The Ant Java process runs from `project.rootDir`. Sources: [argument assembly](https://github.com/mbeddr/mps-gradle-plugin/blob/649c88d39d82711cb85e4093b096e29fe2027529/src/main/kotlin/de/itemis/mps/gradle/RunAntScript.kt#L54), [Java selection](https://github.com/mbeddr/mps-gradle-plugin/blob/649c88d39d82711cb85e4093b096e29fe2027529/src/main/kotlin/de/itemis/mps/gradle/RunAntScript.kt#L91).

A conservative adapter should collect `-Dmps.home=...` and `-Dmps_home=...` from the effective argument sequence, splitting on the first `=`. Equal values can be unified; conflicting duplicates or differing spellings should be reported as ambiguous. Their interpretation ultimately depends on the generated Ant script. Do not arbitrarily assume one spelling wins. If absent, report MPS as unknown: it may come from an Ant XML property, property file, or execution-time customization. Do not infer MPS from an arbitrary Ant JAR location.

Normalize supported executable values such as strings and files using Gradle's semantics. Providers and arbitrary `Any` values require explicit compatibility handling; blindly calling `toString()` can report a provider description instead of a path. If no explicit Java is configured, report that source as Gradle's default Java rather than asserting JBR. Bare executable names and relative paths need validation against the actual launch behavior before promotion to a usable Java home.

Configuration performed in `doFirst` or other task actions is unavailable without executing those actions. Do not execute the build just to discover its runtime. Exotic conflicts and execution-time overrides can remain outside the initial heuristic's scope.

### Structured task APIs (optional additional coverage)

The mbeddr plugin also contains `MpsCheck`, `MpsGenerate`, `MpsExecute`, and `Remigrate` tasks with `mpsHome: DirectoryProperty`; they inherit `JavaExec` and its launcher configuration. These are stronger discovery sources than parsed Ant arguments. Explicit Java executable overrides must be reconciled with `javaLauncher` instead of assuming the launcher always wins. Sources: [MpsCheck](https://github.com/mbeddr/mps-gradle-plugin/blob/649c88d39d82711cb85e4093b096e29fe2027529/src/main/kotlin/de/itemis/mps/gradle/tasks/MpsCheck.kt#L20), [MpsGenerate](https://github.com/mbeddr/mps-gradle-plugin/blob/649c88d39d82711cb85e4093b096e29fe2027529/src/main/kotlin/de/itemis/mps/gradle/tasks/MpsGenerate.kt#L26), [MpsExecute](https://github.com/mbeddr/mps-gradle-plugin/blob/649c88d39d82711cb85e4093b096e29fe2027529/src/main/kotlin/de/itemis/mps/gradle/tasks/MpsExecute.kt#L22), [Remigrate](https://github.com/mbeddr/mps-gradle-plugin/blob/649c88d39d82711cb85e4093b096e29fe2027529/src/main/kotlin/de/itemis/mps/gradle/tasks/Remigrate.kt#L27).

`MpsMigrate` is different: it declares both `javaExecutable` and `javaLauncher` itself, and explicitly gives `javaExecutable` precedence. Source: [MpsMigrate launch](https://github.com/mbeddr/mps-gradle-plugin/blob/649c88d39d82711cb85e4093b096e29fe2027529/src/main/kotlin/de/itemis/mps/gradle/tasks/MpsMigrate.kt#L115).

Specific Languages 2.1 sets conventions on these mbeddr task types when `de.itemis.mps.gradle.common` is also applied, so coexistence is normal; avoid reporting duplicate results as an error. Source: [integration](https://github.com/specificlanguages/mps-gradle-plugin/blob/249b3f55ec3bd8e7ebb39ecb31de85e0e8f6c2ad/subprojects/mps-gradle-plugin/src/main/kotlin/com/specificlanguages/mps/internal/mbeddr_plugin_integration.kt#L25).

### Downloaded but unprepared JBR

The mbeddr `downloadJbr` task has public `jbrDir`, `javaExecutable`, and `javaLauncher` accessors. Its directories are configured before extraction; `downloadJbr` depends on `extractJbr`, which performs extraction in its action. Reading its path is therefore not evidence that Java exists. Reading its launcher can fail until extraction has happened. A download task alone is also not evidence that a particular build task uses that runtime. Sources: [download task API](https://github.com/mbeddr/mps-gradle-plugin/blob/649c88d39d82711cb85e4093b096e29fe2027529/src/main/kotlin/de/itemis/mps/gradle/downloadJBR/Tasks.kt#L23), [extraction and task wiring](https://github.com/mbeddr/mps-gradle-plugin/blob/649c88d39d82711cb85e4093b096e29fe2027529/src/main/kotlin/de/itemis/mps/gradle/downloadJBR/Plugin.kt#L39).

Report missing directories as requiring project preparation. Do not attach arbitrary task dependencies or invoke `build`/`setup` as part of the first discovery implementation.

## Probe architecture and mops integration

Use the project's Gradle wrapper with an injected Groovy init script and an isolated report task, emitting a versioned JSON document into a temporary output file. Preserve Gradle stdout/stderr as diagnostics rather than trying to parse normal console output. Gradle documents `-I` and initialization callbacks in its [init-script guide](https://docs.gradle.org/current/userguide/init_scripts.html), and `projectsEvaluated` after project build scripts in the [build lifecycle guide](https://docs.gradle.org/current/userguide/build_lifecycle.html).

Register the probe for the selected build, inspect finalized project configuration, and query runtime providers during the report task. Disable configuration caching for the initial probe implementation unless its access pattern is tested for compatibility. Avoid importing plugin classes on the init script classpath: discover the extension by name and recognize known task classes through their runtime class hierarchy or the applied plugin's classloader. Gradle decorates task classes, so exact equality with the concrete task class name is insufficient. Realizing tasks can execute their configuration callbacks even when task actions do not run.

Keep each candidate's MPS and Java together, with its project/task and source. A simple result containing paths and an optional diagnostic is sufficient. Catch provider failures so a broken Java provider does not hide a usable MPS result. Deduplicate identical pairs; choose the closest applicable Gradle project, then a deterministic first conventional match, and show its source so an incorrect guess is easy to recognize. Do not combine MPS from one task with Java from another. Included builds and exotic execution-time customizations can remain outside initial coverage.

mops startup resolves explicit `mpsHome`; it chooses explicit `javaHome` or searches for bundled Java. `DaemonContext.fromLivePaths` requires existing paths. A discovery command can display configured-but-missing paths without passing them into daemon startup. It can print reusable `--mps-home` and `--java-home` values for resolved pairs. Automatic discovery during every command and writing persistent configuration are separate design decisions. Sources: [MopsCommand](https://github.com/specificlanguages/mops/blob/4d72734ca3c23dc092e867ca01e6ab20bf21efc2/cli/src/main/kotlin/com/specificlanguages/mops/cli/MopsCommand.kt#L79), [DaemonContext](https://github.com/specificlanguages/mops/blob/4d72734ca3c23dc092e867ca01e6ab20bf21efc2/protocol/src/main/kotlin/com/specificlanguages/mops/protocol/DaemonContext.kt).

Initial implementation tests should cover an ordinary Specific Languages project, an ordinary mbeddr project with project-wide defaults, a task override, and paths containing spaces. Verify the report does not execute build task actions. Provider failure and missing-runtime cases merit one straightforward diagnostic test each. More exotic task combinations can be tested when concrete projects require them.
