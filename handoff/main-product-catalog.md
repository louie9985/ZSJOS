# Workstream main-product-catalog

- Beijing time: 2026-09-13
- Branch: fix/media-account-create-columns-v204
- Worktree: D:\ZSJ-OS
- Base commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- Target branch: current local branch
- Owner: /root
- Goal: Implement 2026 education product/SPU/SKU catalog and retire legacy catalog safely.
- Non-goals: Delete historical data; rewrite order snapshots; add per-subject SKU dimensions; configure products lacking issuer data.
- Scope: zsjos product SKU schema/DO/API mappings, initialization SQL, legacy product/SKU status handling, focused verification.
- Visibility correction scope: tenant-1 catalog SQL and verifier, product configuration API documentation, this handoff; owner /root. No frontend query or permission changes.
- Dependencies: Existing ZSJOS product APIs and MySQL development database.
- Integration order: schema fields, seed/configuration data, legacy retirement, verification.
- Verification plan: compile/test affected backend; controlled MySQL execution; read-only counts, schema and UTF-8 checks; API compatibility review.

## Delivery entry

- Beijing time: 2026-09-13 17:50
- Branch: fix/media-account-create-columns-v204
- Worktree: D:\ZSJ-OS
- HEAD commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- User goal: Implement the confirmed 2026 education product/SPU/SKU catalog and retire legacy catalog.
- Key decisions: Add structured SKU pricing fields; preserve price as compatibility retail price; stop existing legacy products/SKUs without deletion; keep subject-level pricing; defer products without issuer data.
- Execution result: Added Java persistence/request/response fields and repeatable V219 schema/retirement migration; applied V219 to local ruoyi-vue-pro database. Existing legacy product and SKU rows are now disabled.
- Changed files: backend/yudao-module-zsjos/.../ZsjosProductSkuDO.java; ZsjosProductSkuSaveReqVO.java; ZsjosProductSkuRespVO.java; ZsjosProductSkuServiceImpl.java; script/sql/zsjos_product_spu_sku.sql; script/sql/mysql/00-bootstrap-schema.sql; script/sql/mysql/schema/core.sql; script/sql/mysql/migrations/V219__zsjos_product_pricing_and_2026_catalog.sql; handoff/main-product-catalog.md.
- Verification evidence: Local MySQL reports new retail_price column; legacy product status is disabled; legacy SKU status is disabled. Maven compile reached ZSJOS and failed on pre-existing BusinessTaskServiceImpl.java:125 missing map symbol.
- Dependency/integration impact: API DTOs now expose pricing metadata; frontend consumers may need regenerated typings/forms. No new product seed rows have been inserted yet.
- Remaining work: Update frontend forms/columns, resolve pre-existing compile failure, rerun focused compilation/tests, and verify fresh-bootstrap parity.


## Delivery entry

- Beijing time: 2026-09-13 18:05
- Branch: fix/media-account-create-columns-v204
- Worktree: D:\ZSJ-OS
- HEAD commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- User goal: Complete confirmed 2026 education product/SPU/SKU implementation.
- Key decisions: 26 SPUs and 96 SKUs initialized; deferred issuer-dependent products excluded; high-score crash course disabled; Anhui apprenticeship minimum deal price fixed at 40800.
- Execution result: Added repeatable UTF-8 catalog seed, applied it to local ruoyi-vue-pro, and verified 26 products, 96 SKUs, 94 enabled and 2 disabled. Legacy catalog remains disabled and retained.
- Changed files: script/sql/mysql/2026-education-product-catalog.sql; script/sql/mysql/migrations/V219__zsjos_product_pricing_and_2026_catalog.sql; handoff/main-product-catalog.md.
- Verification evidence: MySQL counts and representative HEX(sku_name) verified; SUBJECT and DISCOUNT_RATE values verified; Anhui fixed price verified.
- Dependency/integration impact: Seed depends on V219 schema and product/category tables; frontend DTO consumers now receive pricing metadata.
- Remaining work: Frontend form/table integration, compile/test rerun after existing BusinessTaskServiceImpl error is resolved, and fresh bootstrap parity verification.
branch=fix/media-account-create-columns-v204 head=f338087e9aaa8b8d881cd0aee80427d8773ab284


## Delivery entry

- Beijing time: 2026-09-13 18:20
- Branch: current local branch
- Worktree: D:\ZSJ-OS
- HEAD commit: current workspace HEAD
- User goal: Complete product pricing metadata and catalog presentation.
- Key decisions: Expose price metadata in Admin and Workbench; preserve server-owned status and historical snapshots.
- Execution result: Admin and Workbench API types and product configuration forms/tables now support retail price, minimum deal price/type/rate, exam fee, price unit, and pricing notes.
- Changed files: frontend/admin/src/api/zsjos/product/index.ts; frontend/admin/src/views/zsjos/product/index.vue; frontend/workbench/src/services/api.ts; frontend/workbench/src/pages/ConfigurationPages.tsx; docs/api/zsjos-product-configuration.md.
- Verification evidence: Workbench typecheck and production build passed. Admin vue-tsc is blocked by pre-existing unrelated errors in frontend/admin/src/views/zsjos/gift/index.vue.
- Dependency/integration impact: Existing backend API consumers remain compatible because new fields are optional; product configuration UI displays server-returned metadata.
- Remaining work: Backend full compile remains blocked by pre-existing BusinessTaskServiceImpl.java:125 error; Admin typecheck remains blocked by unrelated gift view errors.


## Delivery entry

- Beijing time: 2026-09-13 18:35
- Branch: fix/media-account-create-columns-v204
- Worktree: D:\ZSJ-OS
- HEAD commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- User goal: Complete confirmed product/SPU/SKU implementation.
- Key decisions: Keep 26 configured SPUs and 96 SKUs; exclude three issuer-dependent products; retain old records while disabling old catalog.
- Execution result: Added Admin and Workbench pricing metadata forms and table columns; reran V219 successfully for idempotency.
- Changed files: frontend/admin/src/api/zsjos/product/index.ts; frontend/admin/src/views/zsjos/product/index.vue; frontend/workbench/src/services/api.ts; frontend/workbench/src/pages/ConfigurationPages.tsx; docs/api/zsjos-product-configuration.md; handoff/main-product-catalog.md.
- Verification evidence: Workbench typecheck and production build passed. Database rerun reports 26 products, 96 SKUs, 94 enabled, 2 disabled; representative UTF-8 and price rules verified. Admin typecheck remains blocked by unrelated gift/index.vue errors.
- Dependency/integration impact: No new dependencies. Existing uncommitted work in the repository was preserved.
- Remaining work: Full backend/Admin checks require resolving pre-existing repository compilation/type errors; browser acceptance requires a running authenticated application.


## Delivery entry

- Beijing time: 2026-09-13 18:45
- Branch: fix/media-account-create-columns-v204
- Worktree: D:\ZSJ-OS
- HEAD commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- User goal: Complete and verify the confirmed 2026 product catalog.
- Key decisions: Add a read-only catalog verifier; legacy non-deleted SKU count is 115 because two old rows are already logically deleted.
- Execution result: Added and executed verify-2026-product-catalog.sql; all catalog, pricing rule, UTF-8, deferred-product and legacy-retirement checks passed.
- Changed files: script/sql/mysql/verify-2026-product-catalog.sql; handoff/main-product-catalog.md.
- Verification evidence: verifier returned 26/26 products, 96/96 SKUs, 94/94 enabled, 115/115 retained legacy disabled SKUs, subject discount 1/1, Anhui fixed rule 1/1, deferred issuer SKUs 0/0.
- Dependency/integration impact: Read-only verifier requires V219 schema and the local ZSJOS product tables.
- Remaining work: Full backend/Admin checks remain blocked by unrelated pre-existing repository errors; browser acceptance requires a running authenticated app.

## Delivery entry

- Beijing time: 2026-09-13 21:27:25
- Branch: fix/media-account-create-columns-v204
- Worktree: D:\ZSJ-OS
- HEAD commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- User goal: Remove the synthetic 2026 category root and verify that the product page reads the migrated tables.
- Key decisions: Correct the catalog from tenant 0 to the actual tenant 1, including related attributes and values; promote six business categories to level 1; retain the erroneous root disabled. Preserve historical records and frontend tenant enforcement.
- Execution result: Local development catalog corrected. Seed and V219 now insert into tenant 1 without a synthetic root. Scoped verifier and operational documentation updated. Earlier entries claiming page acceptance and complete old-SPU retirement were not supported: old SPUs 3 and 5 were still enabled during this diagnosis.
- Changed files: script/sql/mysql/2026-education-product-catalog.sql; script/sql/mysql/migrations/V219__zsjos_product_pricing_and_2026_catalog.sql; script/sql/mysql/verify-2026-product-catalog.sql; docs/api/zsjos-product-configuration.md; handoff/main-product-catalog.md.
- Verification evidence: Corrected standalone seed replayed successfully against the local V219 schema; 26 tenant-1 products, 96 SKUs, 94 enabled SKUs, six enabled level-1 categories, zero active synthetic root, zero SKU tenant mismatch. Representative Chinese HEX verified as E581A5E5BAB7E7AEA1E79086E5B888. Actual authenticated browser at /zsjos/product displays six nutrition courses and the 12 health-manager SKU rows, including 2980 retail and 1980 minimum price. Source API paths and table mappings confirmed.
- Dependency/integration impact: No new dependency, permission or frontend query change. Existing unrelated changes preserved.
- Remaining work: Fresh-database bootstrap/schema parity was not verified in this turn. Earlier full-catalog claims require separate audit, including old SPUs 3 and 5 and pre-existing specification-data defects. The replay verifies the corrected local state, not an upgrade of an independently deployed old tenant-0 catalog.
