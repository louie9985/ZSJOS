# FMS Workbench Migration

## Runtime boundary

FMS is available in both frontend runtimes:

- Vue Admin remains supported under `frontend/admin/src/views/fms` and `frontend/admin/src/api/fms`. It is not deleted, redirected, or scheduled for retirement by this migration.
- React Workbench exposes the same 26 user-facing FMS pages under `/fms/**`.
- Both runtimes call the existing `yudao-module-fms` and System APIs. Workbench does not introduce a duplicate FMS aggregation backend.
- Menus, page access, operation permissions, dictionaries, account-set membership, and write access remain server-owned.

## Workbench routes

| Area | Routes |
| --- | --- |
| Home | `/fms/home` |
| Voucher | `/fms/voucher/create`, `/fms/voucher/list`, `/fms/voucher/statistics` |
| Ledger | `/fms/ledger/general`, `/fms/ledger/detail`, `/fms/ledger/subject-balance`, `/fms/ledger/quantity-detail`, `/fms/ledger/quantity-general`, `/fms/ledger/multi-column`, `/fms/ledger/auxiliary-detail`, `/fms/ledger/auxiliary-balance` |
| Reports | `/fms/report/balance-sheet`, `/fms/report/income-statement`, `/fms/report/cash-flow-statement` |
| Closing | `/fms/closing/period` |
| Configuration | `/fms/config/account-set`, `/fms/config/subject`, `/fms/config/auxiliary`, `/fms/config/initial-balance`, `/fms/config/currency`, `/fms/config/digest`, `/fms/config/voucher-word`, `/fms/config/voucher-template`, `/fms/config/finance-parameter`, `/fms/config/finance-indicator` |

The route resolver lazy-loads FMS pages and ECharts, so users without FMS access do not download the finance page chunks.

## Functional coverage

- The account-set provider restores the authorized default initialized account set, persists a successful switch, and loads the current accounting month. A failed default-account API update does not change local state.
- Voucher pages cover create/update permission separation, copy, templates, print settings, import/export, review, delete, move, tidy, and attachment operations.
- Closing covers period checks, profit-and-loss carry-forward, regular and special schemes, templates, generated vouchers, closing, and reverse closing.
- Initial balances preserve auxiliary combinations, save leaf subjects only, aggregate parent balances, support import/export and trial balance, and become read-only after closing.
- Subject configuration covers server dictionaries, hierarchy, auxiliary/currency/quantity accounting, usage restrictions, and confirmed historical-data migration.
- Ledger and report pages retain query, export, print, formula, adjustment, empty, error, and retry behavior according to their server permissions.

## Verification baseline

The migration uses focused verification rather than a heavyweight all-page E2E suite:

- `npm test`
- `npm run typecheck`
- `npm run build`
- `git diff --check`
- Desktop and mobile browser smoke checks for the FMS shell, account-set switch, home charts, subject list, initial balances, finance indicators, voucher entry, and closing schemes.

Vue FMS directories must remain unchanged when completing or reviewing Workbench migration work.
