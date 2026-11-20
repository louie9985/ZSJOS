# local-task-border-compat — 2026-11-20

- Owner: Codex; environment: local; branch: main; worktree: `D:\ZSJ-OS`; base: `82b5e11532bd13cb7965f80edd436a8a9eb25cdc`; integration: None.
- Goal: fall back to a static pending-task border without mask support. Own shared workbench lead-management CSS, lead/student task indicator docs and this record. No backend, data, dependency or deployment changes.
- Dependencies: existing card border and decorative span. Verify animated, unsupported and reduced-motion paths in Chromium at desktop/mobile widths and check scoped diff.

Delivered locally: shared CSS now enables the decorative layer only after positive support and motion checks; both feature docs updated. Both existing Playwright suites passed (7 desktop/mobile scenarios). Chrome 157 computed-style checks at 390/1440 verified the static red border with no overlay or animation when the mask feature query was forced false. Scoped diff check passed. No legacy binary or user device verified; not deployed. Ownership released.
