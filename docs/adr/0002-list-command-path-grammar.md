# list command uses bounded MPS path navigation

`mops list` uses a path-like grammar over the loaded MPS project: the omitted target is the project root, `/` is the loaded repository root, bare relative paths begin with a project module, and serialized module or model references may replace the corresponding path prefix. Node references remain whole-target values because their serialized form contains `/`. Depth is bounded and includes the resolved target itself so `--depth 0` acts as an existence check.

This keeps the command stateless and compact for agents while avoiding the ambiguity of unqualified model-name lookup. Names are resolved segment by segment and ambiguity fails at the segment where it occurs; callers use references or model-local node IDs when names are not unique. These paths are MPS navigation paths, not filesystem paths, and the command reads the loaded MPS state through the daemon.
