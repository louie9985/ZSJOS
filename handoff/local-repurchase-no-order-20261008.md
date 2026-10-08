# Workstream: local-repurchase-no-order-20261008

- Environment: local (ZSJOS_AGENT_ENV and /etc/zsjos/agent-environment absent).
- Branch/worktree/base: main; D:/ZSJ-OS; d2f836006ff07b5517bcd6d485d72df788bf3cb0.
- Owner: this chat /root. Target branch/integration order: None.
- Goal: user explicitly permits existing customers with Leads but no effective orders through the unified historical-customer purchase entry; this supersedes the previous effective-first-order contract for that entry.
- Non-goals: changing other repurchase entry preconditions, Lead ownership/status, permissions, SQL, dependencies, deployment, shared services or Git operations.
- Ownership: RepurchaseCustomerService.java, SalesOrderServiceImpl.java; RepurchaseCustomerServiceTest.java, SalesOrderServiceImplTest.java under backend/yudao-module-zsjos; docs/api/zsjos-sales-order.md; frontend/workbench/docs/customer-repurchase.md; this record.
- Dependencies: existing identity/permission/tenant/draft/duplicate/approval mechanisms. Prior runtime repair delivered and released ownership; preserve its uncommitted changes. Independent workstreams use separate records.
- Verification: focused Maven order/identity/draft/transaction tests; inspect both React and Vue consumers of canRepurchase; scoped diff and documentation checks. No UI shape/layout changes; deployed authenticated API acceptance reported separately.

## Delivery — 2026-10-08

- Removed prior-effective-order prerequisite from unified customer preflight and matched-customer submission only; draft validation inherits the updated preflight. Existing feature/tenant/identity/active-repurchase/duplicate-payment checks remain. Original Lead and Opportunity remain unchanged; new order belongs to current submitter.
- Updated API and Workbench documentation to record the explicitly approved exception and unchanged legacy entry boundaries. Preserved pre-existing runtime-repair edits in shared files.
- Verification: Maven reactor focused suite PASS, 113 tests, 0 failures/errors/skips. Covers customer preflight without effective orders, both sales/education submission with and without effective orders, current ownership and idempotency, identity/permission/tenant denial, duplicate guard, transaction and purchase-draft behavior. Log: C:/Users/EDY/AppData/Local/Temp/zsjos-repurchase-no-order-tests.log.
- Both consumer source paths inspected independently: React ExternalRepurchasePage and SalesOrderEntryModal use canRepurchase and customer_repurchase drafts/submitCustomerRepurchase; Vue ExternalRepurchaseDialog uses canRepurchase and createCustomerRepurchase. Neither imposes an independent effective-order gate; response and request shapes unchanged. This is static contract evidence, not browser/runtime evidence.
- Scoped git diff --check passed. No frontend source/layout/bundle change, database change, dependencies, account permissions, service lifecycle, deployment or Git mutations performed.
- Unverified: real authenticated Admin/Workbench flow against deployed API, MySQL/BPM/payment integration. Existing running deployment is not updated by this source change. Ownership released.
