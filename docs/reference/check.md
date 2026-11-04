# mops check

Generated from `mops check --help` by `./gradlew :cli:updateReference`. Do not edit this page. Global options are listed
in the [index](index.md).

Run MPS checks.

```text
Usage: mops check [-h] [COMMAND]
Run MPS checks.
Commands:
  model    Run MPS's full model check over one model.
  module   Run MPS's full check over one or more project modules.
  project  Run MPS's full check over every project module and its models.
  help     Display help information about the specified command.
Options:
  -h, --help                Show this help message and exit.
```

## mops check model

Run MPS's full model check over one model.

```text
Usage: mops check model [-h] [--format=FORMAT] [--limit=N] MODEL_TARGET
Run MPS's full model check over one model and report the findings. Exits 1 when
any finding is an error.
Parameters:
      MODEL_TARGET          Model name or serialized model reference.
Options:
      --format=FORMAT       How to print findings: human (a readable list
                              sorted by severity, then # metadata lines;
                              default) or jsonl (one finding object per line,
                              then a summary record).
  -h, --help                Show this help message and exit.
      --limit=N             Maximum findings to report, most severe first.
                              Defaults to 20 so a broken model does not flood
                              the output; 0 means unlimited.
```

## mops check module

Run MPS's full check over one or more project modules.

```text
Usage: mops check module [-h] [--format=FORMAT] [--limit=N] MODULE...
Run MPS's full check over one or more project modules and their models. Exits 1
when any finding is an error.
Parameters:
      MODULE...             Project module name or serialized reference.
Options:
      --format=FORMAT       How to print findings: human (a readable list
                              sorted by severity, then # metadata lines;
                              default) or jsonl (one finding object per line,
                              then a summary record).
  -h, --help                Show this help message and exit.
      --limit=N             Maximum findings to report, most severe first.
                              Defaults to 20 so a broken model does not flood
                              the output; 0 means unlimited.
```

## mops check project

Run MPS's full check over every project module and its models.

```text
Usage: mops check project [-h] [--format=FORMAT] [--limit=N]
Run MPS's full check over every project module and its models. Exits 1 when any
finding is an error.
Options:
      --format=FORMAT       How to print findings: human (a readable list
                              sorted by severity, then # metadata lines;
                              default) or jsonl (one finding object per line,
                              then a summary record).
  -h, --help                Show this help message and exit.
      --limit=N             Maximum findings to report, most severe first.
                              Defaults to 20 so a broken model does not flood
                              the output; 0 means unlimited.
```
