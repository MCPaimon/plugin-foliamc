# Architecture & Technical Standards — FoliaMC

This repository is the standalone `foliamc` platform of MCPaimon. It is published
as `io.github.mcpaimon:foliamc` and shaded into the runnable `MCAgentsFoliamc`
plugin jar via the Shadow plugin.

## Scope

- Platform-specific implementation that hosts the AI provider on FoliaMC servers
  and manages MCExtension plugins.
- Concurrency and region rules: see `.agents/platforms/foliamc/concurrency.md`.

## Dependencies

Depends on `io.github.mcpaimon:api` and `io.github.mcpaimon:common` using
**static** versions (for example `2026.0.7-8`), plus the relevant
`io.github.mcengine` artifacts. Versions are pinned because each module is
released independently and may diverge.
