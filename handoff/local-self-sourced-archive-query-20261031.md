# local-self-sourced-archive-query-20261031

- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Owner: Codex /root; branch: main; worktree: D:/ZSJ-OS; base: f25209c0b48765ef9b54463f6225f6a78e2363ae; integration: None.
- Goal: sales/education self-sourced archive query, mandatory candidate selection and independent lead creation; preserve ordinary submission duplicate restrictions.
- Non-goals: historical repairs, permission grants, deployment, dependencies, identity merges.
- Ownership: lead submission Controller/VO/Service, focused archive query service and tests, ZsjosErrorCodeConstants.java; Workbench LeadSubmissionPage.tsx and services/api.ts; Admin LeadCreateDialog.vue and api/zsjos/workbenchMenus.ts; directly affected lead API and permission documentation; this record.
- Dependencies: existing Person duplicate candidate query and identity locks; region source decision pending because Person has no region fields. User-approved self-sourced candidate visibility includes complete name/mobile/wechat, not historical lead details.
- Verification: focused backend tests/compile, both frontend static checks, browser flows with zero/one/many candidates, stale query and denied requests; target schema read-only check if environment available.

- Confirmed correction: only name, mobile and WeChat belong to archive reuse. Region remains a separately entered field of this lead. Existing duplicate-activation documentation is superseded only for self-sourced entry points by the user's explicit decision.


## Delivery — 2026-10-31
- Implemented additive archive-query APIs and read-only compatibility probes, exact existing contact/verified-alias matching, mandatory candidate membership and snapshot revalidation under tenant-scoped locks. Selected identity is copied into the new Lead without Person mutation; self-sourced creation bypasses activation, weak review and the single-active-lead service guard. Ordinary submission/review behavior remains.
- Both existing frontend entry points show full name/mobile/WeChat, default one candidate, require a choice for multiple, preserve separately entered region, invalidate stale query results and keep retry errors. No schema change, database write, service restart, grants or deployment.
- Ownership additionally includes the two existing focused Java test classes and isolated browser fixtures under frontend/{admin,workbench}/tmp/self-sourced-archive/. Fixture data is synthetic and excluded from production flows.
- Passed Maven focused suite: 53 tests (9 archive, 35 submission, 4 ordinary contact activation, 5 controller permissions), backend compilation, Workbench typecheck and 7 existing step guards, scoped diff checks.
- Real Chrome fixture flows passed on both frontends, sales/education at 1440 and 390 widths: zero/one/multiple candidates, required selection, readonly copied identity, no-match editing, retry; Workbench also stale async response suppression. Screenshots retained under the fixture directories. These are fixture UI checks, not real-account/API acceptance.
- Admin typecheck reports only pre-existing unused handleRefresh in src/views/mes/pro/task/edit/index.vue:86. Extended unchanged LeadSelfSourcedTransactionTest: 18 tests, 14 failures/errors from missing mandatory follow-up time and incomplete provider attribution fixtures; not represented as a passing transaction chain.
- Read-only local Docker ruoyi-vue-pro check: V355 present, name/mobile/wechat_id present, zero uk_tenant_active_person indexes on Lead/Opportunity. This is local schema evidence only, not server schema or production acceptance.
- Remaining: deploy/restart authorization and real-account/API transactional acceptance on target environment; no historical submissions repaired. Ownership released.

## Review fixes registration — 2026-10-31
- Same local/main workstream and owner resumed; environment variable/file remain absent. Scope: archive selection service/tests, submission controller/logging tests, Admin query API, directly affected API contract and this record. No business data, deployment or shared services changed.
- Fix no-selection checks to use non-locking candidate lookup; authoritative new-Person creation retains existing contact-reservation-before-Person-lock ordering and concurrent conflict detection. Selected-Person path never reserves contacts.
- Verify scoped Java regressions, actual log sanitizer behavior, Admin error preservation and typecheck. MySQL concurrency execution will be reported separately from unit/static evidence.

## Review fixes delivery — 2026-10-31
- Fixed all three accepted findings: shared create VO query-contact fields sanitized at all three create endpoints; no-selection archive lookup does not take Person locks before createNew contact reservation; Admin query preserves actionable business errors.
- Passed 50 focused Java tests (9 archive selection, 35 submission, 6 controller/logging). Logging test invokes the actual ApiAccessLogFilter sanitizer for every create method; selection tests assert no FOR UPDATE call on the no-selection branch. Maven compile/test succeeded.
- Passed Chrome Admin fixture flows for both entry identities at 1440/390, now exercising the real Axios response interceptor with a synthetic business-error response and asserting the exact inline error message. First adapter attempt lacked Axios request metadata; fixture corrected and all scenarios passed. No production transport changes beyond preserveBusinessError.
- Controlled MySQL REPEATABLE READ two-connection lock-order scenario passed: ordinary flow reserves contact first, self-sourced non-locking precheck completes, ordinary inserts/commits Person, self-sourced reservation sees the committed Person without deadlock or a second insert. This tests SQL lock behavior, not the full Java HTTP transaction. Synthetic tables retained in separate schema self_archive_lock_verify_20261031222152; no business schema writes or destructive cleanup.
- Admin typecheck still reports only unrelated src/views/mes/pro/task/edit/index.vue:86 unused handleRefresh. Scoped diff checks passed. Target deployment/real-account acceptance remains undone; no service restart or permission changes. Ownership released.

## User correction registration — 2026-10-31
- Resume same local/main worktree and owner; prior implementation now committed by external work. Inspect current inputs before edits. User explicitly replaces readonly/reuse semantics: original single set of form inputs remains editable, archive choice links Person only; submitted name/contact remain Lead snapshots, no automatic Person writes.
- Ownership adds Workbench lead-submission.css plus existing two frontend forms, archive service/tests, fixtures and directly affected docs. Query contact changes invalidate selection; name edits preserve it. Candidate presentation uses existing components and responsive labelled fields.
- Verification: focused service tests/compile, both frontend static checks, desktop/mobile browser checks for retained values, missing archive contact, selection and stale queries.

## Correction delivery — 2026-10-31
- Current base e7b529ff0b19bb413c4c03ccf5a9a08b964b0c83. Removed duplicate search inputs on both frontends; restored editable original name/mobile/WeChat fields. Lookup sits after original contact/region fields; labelled responsive candidate cards replace the inline text run and the agreed association confirmation replaces overwrite/reuse wording.
- Selection no longer writes form values or request identity. Server requires submitted contacts to equal queried contacts for both selected and new-person paths, validates selected membership/snapshot, and preserves submitted name/contact as Lead snapshots without modifying Person. Contact edits invalidate selection, name edits retain it.
- Passed 44 focused backend tests (35 submission + 9 archive); reran 9 archive tests after extending missing-Person-mobile / submitted-new-mobile case, passed. Workbench typecheck and 7 step guards passed. Both frontend Chrome fixtures passed sales/education at desktop 1440/mobile 390 with original editable fields, preserved name/contact, selection invalidation, default single selection, required multiple choice, retry; Workbench stale-response case passed. Inspected screenshots in fixture directories.
- Admin typecheck remains blocked only by existing MES handleRefresh unused error. Global stylesheet guards: 26 passed, 4 failed on unrelated preview-page styles and pre-existing undefined tokens; changed stylesheet uses existing crm tokens. No dependency, database, permission, service or deployment changes. Real-account/API acceptance not performed. Ownership released.
