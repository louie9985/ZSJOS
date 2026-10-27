# local-student-table-alignment-20261027
- Environment: local (environment variable and environment file absent).
- Goal: repair student table header/body alignment, preserving preferences and scrolling.
- Non-goals: business data, APIs, permissions, deployment, dependencies.
- Branch: main; worktree: D:\ZSJ-OS; base: 8279212fb97129ba6b006dbb0452ad843dfde4b5.
- Owner: Codex current chat.
- Scope: frontend/workbench/src/components/BusinessTable/index.tsx, frontend/workbench/docs/business-table.md, frontend/workbench/test/student-table-alignment-browser.py, this record.
- Dependencies: existing BusinessTable/student fixture and Chrome/Playwright; preserve unrelated changes.
- Verification: reproduce cell geometry mismatch, desktop/mobile, scrolling, saved widths, resizing, shared table regressions and focused tests.
- Target branch/integration order: None.
- Scope update: fix belongs in shared frontend/workbench/src/styles/components/business-table.css; index.tsx remains unchanged. Browser evidence: narrow saved columns produced header widths 82/68/82 vs body 80/62/60; fixed header layout plus definite width restores equality.

## Delivery
- Shared sticky header now uses fixed layout with a definite 100% width, honoring measured body colgroup widths. Body auto sizing, hidden columns and saved preferences remain unchanged.
- Reproduced narrow saved column mismatch before fix; browser regression compares every column width/left edge (<1px tolerance).
- PASS student alignment at 2200/1440/1280; mobile 390 retains student list and verifies shared table; horizontal scroll, drag, reload passed.
- PASS existing business-table-width-browser.py (hidden columns, numeric/grouped/native tables, fixed actions, long courses, saved width minimums).
- PASS BusinessTable + StudentServiceColumns focused tests (10 tests). Styles guard: 26 passed, 4 failed on unrelated existing colors/spacing/font tokens/missing tokens; no offending file belongs to this change.
- CSS/docs scoped diff check passed. Browser artifacts: C:\Users\EDY\AppData\Local\Temp\zsjos-table-alignment.
- No deployment or live-data operation. Ownership released.
