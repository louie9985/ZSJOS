# local-test-varargs-20261109

- Environment: local (environment variable and environment file absent)
- Owner: Codex; branch: main; worktree: D:\ZSJ-OS
- Base: 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c
- Goal: remove four Mockito null varargs warnings while preserving sequential return behavior.
- Scope: GiftPurchaseServiceTest.java, NewcomerPoolServiceTest.java, ZsjosProductSkuServiceImplTest.java, ZsjkqtReviewRegressionTest.java under backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service; this record.
- Non-goals: production behavior, dependencies, database, other working changes.
- Dependencies: existing Mockito/Maven; integration target/order: None.
- Verification: scoped diff and four focused tests with reactor compilation.
## Delivery — 2026-11-09 (Asia/Shanghai)
- HEAD/branch/worktree unchanged from registration.
- Changed the four registered test files: chained single-value thenReturn calls remove null-varargs ambiguity while retaining consecutive returns. No production changes.
- Verification: Maven reactor test compilation completed; the four reported warnings are absent. Focused tests: 41 run, 39 passed, 1 failure and 1 error. All modified test methods passed. ZsjkqtReviewRegressionTest has failures in separate untouched Excel-export tests: single_cell_boundary_is_checked_before_save_and_legacy_export_never_truncates line 91 (missing cell), prenatal_summary_roundtrips_and_legacy_reports_remain_readable line 121 (header mismatch). Overall Maven result: BUILD FAILURE.
- Scoped diff: four one-line replacements. Dependencies/integration impact: None. Remaining: separate Excel-export failures are outside this warning fix; no claim of full test-suite success. Ownership released.
