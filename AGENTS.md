# Snoozeloo

Kotlin Multiplatform alarm app — `:androidApp` (Compose), `:shared` (common logic), `iosApp`.

## Agent skills

### KMP conventions

Architecture, layering and library choices for this repo are fixed by the eight `kmp-*` skills (`kmp-module-structure`, `kmp-presentation-mvi`, `kmp-data-layer`, `kmp-di-koin`, `kmp-navigation`, `kmp-error-handling`, `kmp-compose-ui`, `kmp-testing`). Load the matching skill before writing or reviewing code in that area; where a skill and this repo's ADRs disagree, the ADR wins.

### Issue tracker

Issues live in GitHub Issues on `IgorJonski/Snoozeloo`, managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage labels, unchanged: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context — one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

### Research notes

`/research` findings go to `docs/research/<topic>.md`, one file per question, each claim cited to its primary source.

## Requirements and design

- Requirements: `docs/requirements/Snozeloo_Requirements.pdf` — build the **extended** variant (orange items) plus edit/delete of alarms.
- Design source of truth: the Figma copy `https://www.figma.com/design/UJQ4zQnfKrThe6scJpS8fG/Snoozeloo--Copy-`, page **UI**, section **Updates ✅** (node `62-6482`), row "Snoozeloo Extended version". Ignore the Figma link inside the PDF.
