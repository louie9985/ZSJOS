# Workstream: main-account-cover-operator

- ID: main-account-cover-operator
- Goal: Assign account homepage image maintenance to the current operator through server field configuration.
- Non-goals: Other fields, role/menu grants, account data, images, layout, deployment or Git operations.
- Branch / target branch: main / main
- Absolute worktree: D:\ZSJ-OS
- Base commit: e78a02f0ed4eceb9975bf92fb428cc19585c8a16
- Owner: Codex current task
- Ownership scope: This handoff; script/sql/mysql/migrations/V209__media_account_profile.sql; script/sql/mysql/media-account-cover-operator.sql; script/sql/mysql/tools/test_account_cover_operator.py; script/sql/mysql/tools/test_v209_replay.py; backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/account/MediaAccountProfileServiceTest.java; docs/api/media-account-profile.md; frontend/workbench/docs/ui-guidelines.md.
- Dependencies: Existing MediaAccountFieldPolicy, field-config version publication, Admin config editor and Workbench editableFields.
- Integration order: Inspect -> baseline/scoped correction -> controlled SQL replay and permission tests -> development config publication -> documentation and delivery.
- Verification plan: Scoped SQL repeatability and unrelated-field/history/draft preservation; Chinese label HEX; backend permitted/denied image operations; existing frontend checks and browser when available; fresh baseline and read-only comparison.
- Database target: yudao-mysql / ruoyi-vue-pro, tenant 1 published config id 4 version_no 4; cover is UNASSIGNED. Publish version 5 and archive version 4; retain fields other than cover.ownerType, drafts and all account data.
- Authorization: User confirmed the presented responsibility/configuration-source/documentation change on 2026-09-14.
- Status: active

## Delivery entry
- Beijing time: 2026-09-14 16:23:33
- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: e78a02f0ed4eceb9975bf92fb428cc19585c8a16
- User goal: 账号主页图片分配责任为运营设置。
- Key decisions: Server configuration only; use OPERATOR with existing feature/object/current-operator checks. Correct unreleased V209 baseline and add a scoped repeatable correction, not another numbered migration. Preserve required flags, drafts, archived JSON and account data.
- Execution result: Development tenant 1 config id 4/version 4 archived; id 5/version 5 published. Only cover.ownerType changed. Subsequent run is a no-op. Admin already supports OPERATOR; Workbench uses editableFields for controls and ownerType for tags. No production runtime source changes or service restarts.
- Changed files: script/sql/mysql/migrations/V209__media_account_profile.sql; script/sql/mysql/media-account-cover-operator.sql; script/sql/mysql/tools/test_account_cover_operator.py; script/sql/mysql/tools/test_v209_replay.py; backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/account/MediaAccountProfileServiceTest.java; docs/api/media-account-profile.md; frontend/workbench/docs/ui-guidelines.md; handoff/main-account-cover-operator.md.
- Verification evidence: Controlled MySQL correction replay PASS (reordered fields, two affected tenants, custom fields, drafts, archived JSON, explicit other owner and missing cover preserved; UTF-8 HEX; second run unchanged). Development JSON comparison confirms only owner changed; prior JSON retained; account-profile aggregate digest unchanged. Chinese label HEX E4B8BBE9A1B5E688AAE59BBE. Maven reactor compile/test PASS, MediaAccountProfileServiceTest 17/17 including operator upload/clear and director rejection. Workbench profile tests 3/3, typecheck and build PASS. Scoped git diff --check PASS.
- Dependency or integration impact: None; no added dependency or role grants. Profile refresh picks up config version 5; stale writes use existing config-version conflict behavior.
- Remaining work: Full bootstrap/migration verification blocked by duplicate existing V228 versions in core manifest discovery after baseline execution. Full fresh-vs-development schema/data comparison not completed. In-app browser reaches login page without a session; real authenticated API/upload and desktop/mobile UI checks unverified. Admin compatibility inspected at existing config editor, not claimed as browser verification.
- Status: implemented and development configuration synchronized; end-to-end release checks limited as above.
