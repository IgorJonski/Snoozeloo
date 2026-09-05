# Multi-module layout from the first commit

The `kmp-module-structure` skill allows a single-module start, but this repo splits into `:core:*`, `:component:*` and `:feature:*` layer modules immediately (`:shared` becomes the KMP `:app`-role module that assembles them and exports the iOS framework). We pay the Gradle setup cost up front so module boundaries are enforced by the compiler rather than by review, which matters more for an agent-driven codebase than for a human one.
