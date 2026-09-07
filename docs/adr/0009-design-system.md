---
status: accepted
---

# Design system: one light Material 3 scheme, a custom Switch, Material Symbols instead of the Figma Hugeicons

The Figma source of truth is a single light theme with three blues and five greys, two Montserrat weights and Hugeicons glyphs. We map that palette onto Material 3 slots (one `lightColorScheme`, no extended colours, several slots sharing one named value), apply it unconditionally — `SnoozelooTheme` ignores the system dark mode and defines no `darkColorScheme`, a deliberate deviation from the `kmp-compose-ui` skill because the PDF specifies one theme and a copied dark scheme would only mislead — and draw the toggle ourselves because Material's `Switch` is fixed at 52 × 32 while Figma needs 51 × 30 and 41 × 24. The icons are Google Material Symbols, not Hugeicons: the glyphs in the file are Hugeicons **Pro** styles (solid, bulk), the free set is stroke-only and has no solid bell for the splash and Trigger, no Compose library exists for either, and Compose Resources cannot render SVG on Android anyway, so the Figma exports were removed from this public repo and nothing in the app ships them. The full slot, typography, shape, component and splash tables live in [`docs/design/design-system.md`](../design/design-system.md).

Decided in [Design system: theme, typography, icons, splash (#14)](https://github.com/IgorJonski/Snoozeloo/issues/14) on 2026-09-07. Tokens from [`docs/design/README.md`](../design/README.md) (#8); module placement from ADR-0004.

## Considered options

- **`darkColorScheme` as a copy of the light one** — satisfies the skill's wording but lies about supporting dark mode; rejected. A real dark theme is out of scope (PDF: single theme).
- **Material `Switch` scaled with `Modifier.scale`** for the Vibrate row — blurs the shadow and shrinks the hit area; rejected.
- **Hugeicons Stroke Rounded (MIT)** — same family as Figma, but every glyph becomes an outline; the 62–82 dp bell on the empty state, Trigger and splash reads wrong. Rejected.
- **Tracing the Pro glyphs by hand** — still a copy of licensed artwork; rejected.
- **`material-icons-extended`** — frozen at 1.7.3 and built with K1; JetBrains points at Material Symbols XML instead. Rejected.

## Consequences

- The app's icons differ in shape from the Figma frames; layouts, sizes and colours do not. Screen specs (#16–#19) reference the `ic_*` names in `design-system.md`, not the Figma component names.
- The Hugeicons Pro exports are gone from `docs/design/`; do not re-add them. Sizes and roles survive in the README table.
- Two hex values are duplicated once outside `Colors.kt`: the Android host theme (`colors.xml`) needs the splash and window background before Compose runs. iOS holds the same two as an asset-catalog colour set and a PDF.
- Launcher / `AppIcon` artwork is out of scope for the map; the developer draws it later.
