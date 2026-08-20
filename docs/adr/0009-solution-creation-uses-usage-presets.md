# Solution creation uses usage presets

Solution creation defaults to no facets and accepts the intent-oriented presets `not-generated`, `text`, `java`, `java-tests`, and `java-mps-plugin`. This deliberately differs from MPS's producer default, which adds a Java facet: many modern solutions only hold analyzed models, generate plain text, or do not generate at all, so Java compilation and class loading must be requested rather than assumed. Presets configure facets only; dependencies, languages, devkits, and model contents remain separate concerns.
