# mops wrapper

Generated from `mops wrapper --help` by `./gradlew :cli:updateReference`. Do not edit this page. Global options are
listed in the [index](index.md).

Write a project-local mops wrapper using Gradle-discovered paths.

```text
Usage: mops wrapper [-hV] [--output=PATH] [PATH]
Write a project-local mops wrapper using runtime paths discovered from Gradle.

Uses the nearest Gradle wrapper to inspect all projects in its build and writes
mopsw or mopsw.cmd for every discovered MPS project. Pass --project-root to
write only that project's wrapper. Wrappers go directly in each Gradle
project's build directory when it has one detected MPS project; with multiple
MPS projects, they go in mops/<MPS-project-name> beneath it. Reported paths are
relative to the working directory.

No CLI-configured MPS home or daemon is required; Java must be available to run
Gradle. Querying configured providers may download and extract MPS or JBR
distributions; it does not run preparation or language build task actions. If
discovery finds only some paths, it prints them, writes no wrapper, and exits
with status 1. When both paths are known, it writes the wrappers and exits with
status 0, with warnings when the paths are not usable yet, so wrappers can be
written before the project's preparation steps. Warnings and the reason no
wrapper was written go to stderr.
Parameters:
      [PATH]                Starting point of discovery; default is
                              --project-root when supplied, otherwise the
                              working directory.
Options:
  -h, --help                Show this help message and exit.
      --output=PATH         Exact wrapper file to write when one MPS project is
                              selected, absolute or relative to the directory
                              where mops was started.
  -V, --version             Print version information and exit.

Discovery reads mpsDefaults of the com.specificlanguages.mps Gradle plugin, or
RunAntScript tasks of the de.itemis.mps.gradle.common plugin, in every Gradle
project of the build, for the shared MPS and Java homes. MPS projects come from
configured mpsBuilds entries and .mps project markers in the build. Task-action
overrides, included builds, and custom Ant property loading are outside this
heuristic; Specific Languages values are project defaults.

Each wrapper supplies the shared MPS and Java homes and its MPS project
directory, and forwards its arguments to mops. It runs the full path of the
mops installation that wrote it, so mops need not be in PATH. The POSIX
wrappers are executable. When an MPS or Java home that a wrapper names no
longer exists, commands fail and say to run mops wrapper again. --project-root
can also precede `wrapper`.

Provider resolution failures stop discovery and keep Gradle's dependency
diagnostics; check the configured runtime versions and repositories. If a
pinned MPS prerelease is no longer available, update the project's MPS
prerelease version to the newest available one; prerelease artifacts are
deleted periodically.

Path macros: the global option --macro NAME=PATH defines a path macro, such as
one that .mps/libraries.xml or .mps/modules.xml uses as ${NAME}; repeat it for
more path macros. PATH is an existing directory, absolute or relative to the
directory where mops was started. The daemon defines the path macros in MPS
before it opens the project, and test runs pass them to the test worker. A
running daemon started with other path macros is not reused: the command fails,
and after mops daemon stop the next command starts a daemon with the new path
macros. Project-relative paths ($PROJECT_DIR$) and the path macros MPS defines
itself (project, mps_home, platform_lib, lib_ext) need no --macro.

Each wrapper passes the --macro values given to mops wrapper. For a path macro
that .mps/libraries.xml or .mps/modules.xml uses without a --macro value, mops
wrapper guesses the root of the project's Git repository, writes it into the
wrapper, and prints a warning on stderr; rerun mops wrapper --macro NAME=PATH
to set another value. Outside a Git repository, the command fails and names the
path macro that needs --macro.
```
