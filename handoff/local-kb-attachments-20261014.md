# Workstream local-kb-attachments-20261014

- Owner: Codex current chat; environment: local (environment variable and marker absent).
- Branch: main; worktree: D:/ZSJ-OS; base: 7ddd776265db39a8bdec7238867271baf17bbcc5; target branch/integration: None.
- Goal: implement approved online-document attachments, readable-implies-download, selected public attachments.
- Non-goals: standalone-file attachments, Office conversion, template attachments, dependencies, grants, service restart, commits/push.
- Ownership: PMS document attachment classes and integration in document/share/recycle services/controllers/VO/DO; related tests; Admin kb document editor/detail/share/recycle and API plus attachment components/tests; PMS V004 migration, desired schema and verification; docs/api/pms-knowledge-public-share.md and attachment contract; this record. Preserve delivered folder-import creation lock and all unrelated changes. Folder-import record explicitly releases ownership.
- Dependencies: existing Infra FileApi, PMS document permissions, delivered PMS V003; current Vue upload/clipboard/preview components.
- Verification: focused Java tests/compile, Admin scoped checks and browser desktop/mobile fixture, controlled MySQL initial/replay/partial/prerequisite checks; distinguish deployment and live acceptance.
- Status: implementation started.

## Delivery — 2026-10-14 北京时间
- Context: local/main/D:/ZSJ-OS; HEAD unchanged 7ddd776265db39a8bdec7238867271baf17bbcc5. Existing folder-import changes preserved; no branch, commit, push or service lifecycle operation.
- Implemented: online-document attachment upload/clipboard/list/read/recycle integration; transactional bindings and metadata; readable-implies-download confirmed exception; document-scoped file ownership; selected public attachments and guarded public navigation; compatible old update/member APIs. Public selection stored on attachment bindings rather than extending the share table.
- Changed scope: PMS attachment DO/mapper/service/controller/VO plus document/share/recycle integrations and focused tests; Admin document attachment picker/reader, editor/detail/share/recycle and APIs; V004 migration, PMS desired schema/read-only verifier and attachment MySQL verifier; browser fixture; API documentation.
- Verification: backend compile PASS; 39 existing document/recycle/share tests PASS; 4 new attachment service tests PASS; public controller suite rerun with new navigation test PASS (5 tests). Scoped ESLint and diff checks PASS. Admin production build PASS in 39.73 s; later editor width adjustment verified in browser. Full vue-tsc with 8 GB completed with only 12 existing withdrawal API duplicate-identifier errors, no PMS diagnostics. Initial default-heap run exhausted memory.
- MySQL: isolated initial/replay/UTF8 HEX/prerequisite-no-ledger/partial recovery/incompatible-schema recovery PASS; actual PMS V001 through V004 chain matches desired schema. Retained schemas prefix pms_attachment_20261014231757. No business database writes. Read-only local ruoyi-vue-pro preflight found only PMS V001/V002, so V003 dependency is pending and V004 cannot be applied alone.
- Browser: real Chrome production components with synthetic HTTP at 1440/390 widths PASS: ordered uploads, failure/retry, save guard, download entry, full editor save/reopen, explicit share selection. Screenshots C:/Users/EDY/AppData/Local/Temp/pms-attachments-browser-8uc9n5k0; mobile editor/share visually inspected. Mobile editor width corrected after visual inspection.
- Remaining: apply reviewed V003 then V004 and deploy backend/Admin; authenticated Workbench/Admin, real storage and public-origin acceptance remain unverified. Synthetic tests are not runtime acceptance. Office conversion and orphan cleanup excluded. Ownership released.
