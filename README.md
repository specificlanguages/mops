# mops

mops lets coding agents inspect, search, edit, build, check, and test JetBrains MPS projects from a terminal, without
the MPS IDE and without reading `.mps` XML. A per-project daemon keeps the project open between commands, and Code Mode
runs Groovy programs against the native MPS API. [docs/VISION.md](docs/VISION.md) says who mops is for, the principles
it follows, and what is out of scope.

## Install

Download the ZIP distribution from [GitHub Releases](https://github.com/specificlanguages/mops/releases), extract it,
and put its `bin/` directory on `PATH`. To install from a checkout, run `./gradlew installMops`: it installs mops into
`~/.local/share/mops` and links `~/.local/bin/mops`.

mops needs an MPS distribution and a Java runtime. In a project built with Gradle, let mops find both and write a
wrapper:

```sh
mops wrapper
```

It prints the path of each `mopsw` (or `mopsw.cmd` on Windows) it writes. Run the wrapper in place of `mops`. Without a
wrapper, pass `--mps-home PATH` and, when the distribution bundles no Java, `--java-home PATH`.

## A short tour

List the project's modules, or the roots of one model:

```sh
mops list
mops list sample.solution .main
```

Find nodes by concept, and get one of them as JSON by its reference:

```sh
mops find instances jetbrains.mps.baseLanguage.ClassConcept
mops get node --summary 'NODE_REF'
```

Apply a JSON batch of edit operations; `mops explain edit` describes the notation:

When an operation creates or copies a structure-language declaration (a concept, interface, property, link, datatype, or
enumeration member), mops gives it a meta-id (`conceptId`, `propertyId`, `linkId`, `datatypeId`, or `memberId`) the way
MPS does: the declaration's own node ID, or a random number when another declaration in the same scope already has that
ID. A copy always gets a new meta-id; a created declaration keeps a meta-id set in the operation. The response's `hints`
list each assigned meta-id that is still in place when the batch ends: a later `setProperty` in the same batch that
overrides it, or a later `delete` of the node, drops its hint.

```sh
mops edit model --file edit-batch.json
```

Check a model, then build and run the project's tests:

```sh
mops check model sample.model
mops test
```

## Reference

- `mops --help` and `mops <command> --help` describe every command and option.
- `mops explain` prints the notations mops reads and writes, such as node references, edit batches, search scopes, and
  how output separates results from metadata.
- `mops examples` prints task recipes; they are also in the [bundled topic index](skills/mops/references/README.md).
- [docs/reference/](docs/reference/index.md) holds all of the above as Markdown, generated from the command line.
- `mops skill path` prints the path of the bundled agent skill. Ask your coding agent to load it, or symlink its
  directory into your agent's skills directory to follow mops upgrades. `mops skill path daemon` prints the skill for
  daemon coordination, restarts, and recovery.

## Development

See the [developer documentation](docs/development.md) for building, testing, and releasing mops.
