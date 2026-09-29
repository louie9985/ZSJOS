# Workstream: local-order-legacy-vouchers-20260929

## Registration — 2026-09-29 16:22 Beijing time
- Environment: local (ZSJOS_AGENT_ENV unset; no environment file; fallback).
- Owner: Codex current conversation.
- Branch/worktree/base: main; D:/ZSJ-OS; 5fadfb3e9be563ac4cb3e5c142308549944aa465.
- Goal: fix historical order voucher string-array reads and prefix historical /media paths with https://crm.zhongshijian.top as explicitly requested.
- Non-goals: database rewrites, new upload behavior, permissions, dependencies, service restart, commit/push.
- Ownership: SalesOrderServiceImpl.java in backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/order/; new SalesOrderVoucherCompatibilityTest.java in the corresponding src/test service/order/ package; docs/api/zsjos-sales-order.md; this handoff file.
- Dependencies: existing JsonUtils and Infra FileApi only. Other current worktree changes are preserved.
- Contract decision: user-confirmed historical URL handling is an explicit exception to documentation's general live-signature wording; ID-bearing attachments retain signing and unavailable behavior.
- Verification: focused voucher tests and existing SalesOrderServiceImplTest, scoped diff checks, inspect Admin and Workbench consumers; report real endpoint verification separately.
- Target branch/integration order: None.
- Status: active.

## Delivery — 2026-09-29 16:25 Beijing time
- Context: registration branch/worktree/base unchanged; HEAD 5fadfb3e9be563ac4cb3e5c142308549944aa465. Owner: Codex current conversation.
- User goal/result: historical order vouchers beginning with /media now resolve under https://crm.zhongshijian.top; absolute historical URLs remain unchanged. String, object, and mixed arrays retain their order. Non-null distinct file IDs alone are signed.
- Key decisions: historical URL-only references have no invented metadata; existing ID-bearing references never fall back to raw storage URLs on signing failure. Empty references return an empty list; malformed payloads still fail explicitly. No database writes or snapshot rewrites.
- Changed files: registered SalesOrderServiceImpl.java; SalesOrderVoucherCompatibilityTest.java; docs/api/zsjos-sales-order.md; this record. Existing unrelated changes preserved.
- Verification: Maven reactor test completed BUILD SUCCESS (2026-09-29 16:24 Beijing time), 73 tests, zero failures/errors/skips: 67 existing SalesOrderServiceImplTest tests and 6 new SalesOrderVoucherCompatibilityTest tests. Production and test compilation passed. Command: mvn -f backend/pom.xml -pl yudao-module-zsjos -am -Dtest=SalesOrderVoucherCompatibilityTest,SalesOrderServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test. Scoped git diff --check passed.
- Consumer inspection: Workbench SalesOrderDetailCards uses fileUrl directly for image/PDF rendering; Admin management detail consumes the same response and its OrderHistoryFacts reads unrelated history fields. This is source inspection, not browser acceptance. Frontend files and response field names unchanged.
- Dependencies/integration impact: no new dependencies, permissions, schema changes, commits, branches, or service operations. Target branch/integration order None.
- Remaining verification: authenticated real order endpoint and historical media reachability are unverified. A service listens on 48080, but it was not restarted/redeployed with the change; this task does not claim runtime deployment or external image availability.
- Status: local implementation delivered; file ownership released.
