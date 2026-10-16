# Setup and discover the API

{{table:commands|Command}}

Global options may appear before, between, or after command names. Command-specific options belong after their command.

`mops wrapper` prints a `Project root:` line and a `Wrapper:` line for each wrapper it writes. The wrapper goes into the
Gradle project's build directory, so its location depends on the project layout: MPS-extensions, for example, gets
`code/extensions/build/mopsw` on current branches and `build/mopsw` on older ones. Call the wrapper in place of `mops`;
it passes the discovered MPS and Java homes.

Wrappers discover configured distributions and can download them, but do not execute preparation or language build
tasks. Build the project with its own build tool first; most operations need the project languages built. Then run
`mops diagnose project` and build the modules the build left out, such as sandbox or test languages, with
`mops make module`.
