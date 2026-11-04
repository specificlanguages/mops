# mops skill

Generated from `mops skill --help` by `./gradlew :cli:updateReference`. Do not edit this page. Global options are listed
in the [index](index.md).

Locate bundled agent skills.

```text
Usage: mops skill [-h] [COMMAND]
Locate bundled agent skills.
Commands:
  path  Print the absolute path to a bundled SKILL.md.
  help  Display help information about the specified command.
Options:
  -h, --help                Show this help message and exit.
```

## mops skill path

Print the absolute path to a bundled SKILL.md.

```text
Usage: mops skill path [-h] [NAME]
Print the absolute path to a bundled SKILL.md. Load it directly or symlink its
directory into your agent's skills directory. Works without an MPS project or
daemon.
Parameters:
      [NAME]                Skill name: mops (default), mops-daemon (alias:
                              daemon).
Options:
  -h, --help                Show this help message and exit.
```
