# Generator creation simplifies MPS naming

Every created generator is tied to a language and is either embedded in that language's `.mpl` descriptor or persisted separately as a standalone `.mpst` module. mops follows MPS's relationship and persistence model but derives the first repository-wide free namespace using `<language>.generator`, `<language>.generator1`, and so on, instead of copying MPS's separate zero-padded namespace and non-padded directory counters; the simpler convention keeps persisted identities and paths predictable while aliases remain explicit.
