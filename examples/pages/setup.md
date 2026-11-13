# Setup and discover the API

{{table:commands|Command}}

Global options may appear before, between, or after command names. Command-specific options belong after their command.

`mops wrapper` first prints a `mops version:` line with the version and git commit of the running mops, the same as
`mops --version`. Then it prints a `Project root:` line and a `Wrapper:` line for each wrapper it writes. The wrapper
goes into the Gradle project's build directory, so its location depends on the project layout: MPS-extensions, for
example, gets
`code/extensions/build/mopsw` on current branches and `build/mopsw` on older ones. Call the wrapper in place of `mops`;
it passes the discovered MPS and Java homes.

For an MPS project without a Gradle build, give the homes: `mops --mps-home PATH --java-home PATH wrapper`, without
`--java-home` when MPS bundles Java. It writes
the wrapper of the project at or above the working directory, or of `--project-root`, into the project's `build/mopsw`.

When `.mps/libraries.xml` or `.mps/modules.xml` uses a path macro (`${NAME}`), the daemon needs its value. Pass
`--macro NAME=PATH` to `mops wrapper`, and the wrapper passes it on every run. For a used path macro without a value,
`mops wrapper` guesses the project's Git root and prints a warning on stderr; check the guess, and rerun with `--macro`
if it is wrong. A daemon started with other path macros is not reused; run `mops daemon stop` after changing them.

Wrappers discover configured distributions and can download them, but do not execute preparation or language build
tasks. Build the project with its own build tool first; most operations need the project languages built. Then run
`mops diagnose project` and build the modules the build left out, such as sandbox or test languages, with
`mops make module`.
