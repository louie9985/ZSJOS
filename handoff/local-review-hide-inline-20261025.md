# Workstream local-review-hide-inline-20261025

- Environment: local (environment variable unset; /etc/zsjos/agent-environment absent).
- Owner: Codex /root; branch main; worktree D:/ZSJ-OS; base 404387de566fcb9abf4b264f0e005bf0566ea209; target branch/integration order: None.
- Goal: remove inline filled record bodies and metric snapshots from account review cards; retain history dialog and authorized write/revise actions.
- Non-goals: data deletion, API/permission changes, deployment, dependencies, Git operations.
- Ownership: this record; frontend/workbench/src/components/AccountReviewRecord.tsx and AccountProfilePanel.tsx; test/account-review-history.tsx and account-review-history-browser.py; docs/ui-guidelines.md under frontend/workbench.
- Ownership update: docs/api/media-account-profile.md for directly affected presentation contract.
- Dependencies: existing history dialog; preserve pre-existing dirty changes. Previous review-history workstream delivered and released ownership.
- Decision: current explicit user request supersedes the prior documented latest-summary presentation.
- Verification: TypeScript check; existing Chrome fixture/browser flow at desktop/mobile widths including history, actions and no inline record content; scoped content checks.

## Delivery
- Removed inline latest-record body, author/time, attachments and weekly metric preview from AccountReviewRecord; retained unfilled indication, write/revise actions and authorized history dialog. Removed obsolete text prop from consumer/fixture.
- Updated UI/API documentation to match the explicit user instruction; preserved existing dirty work.
- Verification: npm.cmd run typecheck passed; existing account-review-history-browser.py passed Chrome 1440/390 checks, including no inline article/metric preview, write/revise, history pagination, loading/empty/error/retry, permission revocation, account/field switch isolation, attachments and weekly metrics in the dialog. Scoped git diff --check passed.
- Evidence: C:/Users/EDY/AppData/Local/Temp/zsjos-account-review-history; browser uses synthetic HTTP with real React components, not authenticated deployment acceptance.
- No deployment, database changes, commits or external service changes. Ownership released.

## Registration update: cycle-separated history
- Goal: label historical entries by their saved cycle and restrict requirements/statistics to that cycle; format history statistics with existing section headers, field rows and colored value tags.
- Ownership: additionally WeeklyDiagnosisMetrics.tsx, AccountReviewHistoryContent.tsx; existing panel, fixture/browser, UI/API docs and this record. No active overlapping writer observed; preserve dirty content.
- Verification: typecheck and Chrome history suite with 7/14/28 cycle isolation, metric values/styles and desktop/mobile widths. No backend/data changes.

## Delivery: cycle-separated history
- Historical entry headings and positioning requirements use the saved 7/14/28-day field scope; alias adjustment_28d maps to the 28-day contract. Weekly metric snapshots/current results are filtered to that scope, preserving shared submitted judgments and backend snapshots.
- History statistics reuse the existing Section/Fields layout, shallow blue headers and colored status/number/rate tags. Form preview formatting remains owned by its existing component path.
- Verification: npm.cmd run typecheck passed after optional fieldKey handling; account-review-history-browser.py passed full Chrome desktop/mobile suite, with new 7/14/28 scope, period requirement, metric value/tag/header and overflow assertions at 1440/390. Desktop 28-day and mobile 14-day screenshots visually inspected. Scoped diff checks passed.
- Evidence: C:/Users/EDY/AppData/Local/Temp/zsjos-account-review-history/history-{1440,390}-weekly-{7,14,28}.png. Synthetic HTTP/component verification; deployment and authenticated live-account acceptance not performed.
- No data deletion, API changes, dependencies, deployment or Git operations. Ownership released.
