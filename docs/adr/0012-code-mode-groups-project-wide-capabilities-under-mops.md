# Code Mode groups project-wide capabilities under mops

> **Status: accepted.** Amends ADR-0008 and ADR-0011.

Project-wide **Code Mode Extensions** are grouped by purpose beneath the global `mops` namespace, while extensions with a meaningful native MPS receiver remain on that receiver. Built-in capabilities compose through nested namespace receiver types and Groovy extensions: Java parsing is exposed at `mops.parsing.java`, and repository searches at `mops.search`. This keeps the global script surface small, avoids presenting a mutable service registry, and leaves room for `mops.testing` and `mops.editing` when those categories gain executable behavior. A language-oriented namespace and a compatibility contract for external extension bundles are deferred until concrete capabilities require them.
