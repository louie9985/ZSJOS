# PMS knowledge share notification template

## Registration - 2026-10-13 Beijing
- Workstream: local-pms-share-notify-20261013; owner: Codex /root.
- Environment: local (ZSJOS_AGENT_ENV unset; environment marker absent).
- Branch/worktree/base: main; D:/ZSJ-OS; 40ec4b41be4f51dda93ad612ebfc2dc5bde1e17a. Target branch/integration order: None.
- Goal: repair missing pms_knowledge_document_shared template in SQL source and the local Docker development database.
- Non-goals: remote/shared database writes, historical notification resend, member/permission changes, other PMS templates, frontend changes, service restart, dependencies, commits/push.
- Ownership: script/sql/mysql/migrations/pms/V002__knowledge_document_share_notify_template.sql; script/sql/mysql/tools/test_pms_share_notify_template.py; docs/api/pms-knowledge-public-share.md; this record.
- Dependencies: existing PMS V001 module ledger; current System notification template schema and direct NotifyMessageSendApi contract. Existing concurrent points redemption/config/cache changes preserved.
- Preflight: local yudao-mysql MySQL 8.4.11, database ruoyi-vue-pro; target template count 0, matching message count 0; tenant 1 enabled; PMS V001 recorded. Shared/remote application settings exist but do not authorize remote writes.
- Verification: retained isolated MySQL schemas for PMS V001 -> V002, replay, partial application, existing disabled/custom template preservation, prerequisite/conflict/ledger failures including client continuation and recovery; UTF-8 HEX; focused template/share tests; scoped local preflight, apply and zero-write replay; static migration discovery/diff checks. No actual employee notification will be sent for verification.

## Delivery - 2026-10-13 11:43 Beijing
- Context: environment/branch/worktree/owner as registered; HEAD unchanged 40ec4b41be4f51dda93ad612ebfc2dc5bde1e17a.
- User goal: fix missing pms_knowledge_document_shared so sharing knowledge documents to members can generate in-app notifications.
- Decisions: PMS direct System API already sends documentTitle and route; retain that contract. Add PMS V002 after already-recorded V001; leave Core and applied migrations unchanged. System templates are global, with no tenant column; business recipients remain tenant 1 ADMIN users. No automatic historical resend, notification rule, enterprise messaging or UI change.
- Result: added enabled in_app template id 668 to local yudao-mysql / ruoyi-vue-pro and one pms/V002 module ledger row. UTF-8 title/content/summary/nickname verified using HEX decoding against intended values. Other template signatures and all prior entries in both version ledgers unchanged. A second application changed neither target row nor ledger. No employee messages created. Missing templates are not cached by NotifyTemplateServiceImpl, so this insertion requires no service restart.
- Changed files: script/sql/mysql/migrations/pms/V002__knowledge_document_share_notify_template.sql; script/sql/mysql/tools/test_pms_share_notify_template.py; docs/api/pms-knowledge-public-share.md; this record.
- SQL verification: 11 retained MySQL 8.4.11 scenarios passed under pms_notify_verify_20261013113707_*: real PMS V001 then V002, partial template, existing marker, custom disabled template, missing prerequisite/recovery, deleted template, unsupported params, invalid JSON, duplicates, ledger-write failure/recovery, postcondition failure/recovery. Failed CALL with client continuation cannot register success or retain a partial template. Replays preserve valid customization and ledger values; unrelated fixture row preserved.
- Service verification: Maven reactor BUILD SUCCESS, 25 tests (PmsKnowledgeDocumentShareServiceImplTest 8, NotifySendServiceImplTest 7, NotifyTemplateServiceImplTest 10), no failures/errors/skips. Existing share test checks exact template code and documentTitle/route payload; System tests cover rendering/sending. Static database check passed; scoped diff and UTF-8 checks passed.
- Evidence: C:/Users/EDY/.codex/tmp/pms-share-notify-20261013114132/preflight.json and result.json retain scoped before/after signatures outside the repository, with no credentials or recipient payloads.
- Dependencies/integration impact: optional PMS migration discovery automatically includes V002. No baseline/schema/permission change, dependency, branch/worktree operation, commit, push or restart. Other local feature/config/cache changes preserved.
- Remaining: remote/shared environments have not received the patch; apply V002 there under separate target authorization and verify one real new-member share. No authenticated browser or live employee delivery test was performed; source/fixture/local database evidence is not remote acceptance. Ownership released.

## Registration update - 2026-10-13 11:54 Beijing
- Same owner/environment/branch/worktree/base as registration; user explicitly requests local PMS public URL configuration.
- Added ownership: backend/yudao-server/src/main/resources/application-local.yaml (only new top-level pms.knowledge-share block) and docs/api/pms-knowledge-public-share.md (local configuration example). Existing Quartz edits remain owned by their original workstream and are preserved.
- Goal: set http://127.0.0.1:48080 as local PMS public base URL. Non-goals: service restart, other runtime configuration, remote writes. Verification: YAML parsing, scoped diff and preservation of all prior local configuration bytes outside the insertion.

## Delivery - 2026-10-13 11:54 Beijing
- Context: local/main/D:/ZSJ-OS; HEAD unchanged 40ec4b41be4f51dda93ad612ebfc2dc5bde1e17a; owner Codex /root.
- User goal/result: configured local PMS public sharing base URL as http://127.0.0.1:48080 and documented local/LAN use.
- Changed files: backend/yudao-server/src/main/resources/application-local.yaml; docs/api/pms-knowledge-public-share.md; this record.
- Verification: all YAML documents parse; expected PMS property verified; removing only the inserted block reproduces the pre-edit content including prior Quartz changes (line endings normalized for comparison); scoped diff checked.
- Dependency/integration impact: None. No service/database changes, commits or push.
- Remaining: restart the backend using the local profile for configuration to take effect; runtime not restarted or verified in this turn. Loopback URL is only for this computer. Ownership released.

## Registration update - 2026-10-13 member notification presentation
- User goal: named sharer and public/internal document links via common components. Same local/main/worktree/owner/base context.
- Ownership added: PMS share service and its existing test; Workbench MessageDetail.tsx and new PmsShareMessageLinks.tsx/test; Admin notification details and new PmsShareMessageLinks.vue; isolated Workbench pms-share-notification fixture/browser test. Existing owned PMS V002/test/docs remain in scope. Other concurrent files including Workbench api.ts/constants.ts are not owned or edited.
- Plan: snapshot authoritative user nickname (username fallback), public URL and document route; preserve old route param; use common ResourceLink in Workbench and Element Plus ElLink in Admin. Existing unpublished local V002 corrected with exact old-default upgrade guard; retain administrator customizations and historical messages. Verify focused Java/UI tests, both frontend static checks, isolated browser at desktop/mobile widths and MySQL initial/replay/recovery plus local correction. No remote writes or actual member sends.
- Ownership update: frontend/admin/src/api/system/notify/message/index.ts templateParams type corrected to the existing backend Map contract; isolated Admin notification fixture included.

## Delivery - 2026-10-13 13:45 Beijing
- Context: local/main/D:/ZSJ-OS; registered owner/base unchanged. User requested named actor plus two common-component links.
- Result: PMS snapshots nickname (username fallback), public URL and library/document route, retaining old route. Logs omit parameter tokens. Workbench uses ResourceLink; Admin uses ElLink and corrected templateParams Map type. History unchanged.
- Changed files: PMS share service/test; PMS V002 and SQL verifier; Workbench MessageDetail, PmsShareMessageLinks/test and notification fixture; Admin notify API type, both details, PmsShareMessageLinks and fixture; docs/api/pms-knowledge-public-share.md; this record.
- Local DB: exact original default upgraded, ledger preserved, UTF-8 HEX and unchanged replay verified. Preimage C:/Users/EDY/.codex/tmp/pms-share-notify-content-preflight.json.
- Verification: 12 MySQL cases, 25 Java tests, Workbench typecheck, 38 common-link and 9 PMS-link tests passed. Both frontend browser fixtures passed at 1440/390 widths and link clicks; mobile screenshot inspected. Screenshots C:/Users/EDY/AppData/Local/Temp/pms-share-notification-xz9n1gry. Scoped diff check passed.
- Limitation: Admin full typecheck blocked only by 12 pre-existing duplicate-field errors in src/api/zsjos/withdrawal/index.ts; notification errors resolved.
- Remaining: deploy backend/frontends and verify actual member delivery. Existing template cache refreshes on backend restart or System template save. No remote writes, actual messages, historical backfill, service restart, dependencies or Git operations. Ownership released.
