# Course calendar notification configuration

## Registration — 2026-10-13 Beijing time

- Environment: local (ZSJOS_AGENT_ENV and environment file absent).
- Workstream: local-course-notify-config-20261013; owner: current Codex chat.
- Goal: configure tenant 1 course-calendar in-app and WeCom templates/rules and report exact wording.
- Non-goals: real message sending, historical replay, account/role grants, exam notification changes, service restarts, channel credentials or employee preferences.
- Branch: main; worktree: D:\ZSJ-OS; base commit: 45f1edbe24b316b5f5924416c4fc18977658ff5f.
- Ownership: this record; script/sql/mysql/course-calendar-notification-config.sql; docs/operations/course-calendar-notification-config.md; tenant-1 course-scene rules and corresponding new global templates in local MySQL.
- Dependencies: existing System notification schema and CalendarNotificationSceneProvider; concurrent course-attachment and calendar-color work is preserved.
- Verification: controlled MySQL initial/repeat/partial/conflict execution; transaction rollback; target preflight and postconditions; UTF-8/HEX and template variables; read-only channel readiness; no delivery API invocation.
- Target branch / integration order: None.

## Delivery — 2026-10-13 14:53 Beijing time

- Branch/worktree/HEAD: unchanged from registration.
- User goal: fully configure course notification and explain the notification wording.
- Decisions: two channels (in_app/wecom), existing fixed recipient contract, no extra users, neutral course/title/time/remark wording, action none; standalone administrator provisioning instead of rewriting V282 or adding a schema migration.
- Result: local tenant 1 now has enabled templates 672/673 and rules 657/658. Existing exam configuration and concurrent course attachment/color edits preserved. No notification API invoked; pending course intents were 0 before application.
- Changed files: script/sql/mysql/course-calendar-notification-config.sql; docs/operations/course-calendar-notification-config.md; this record. Database: only two new global course templates and two tenant-1 course rules; helper procedure removed after success.
- Verification: MySQL fixtures course_notify_20261013145051_{initial,partial,conflict,prerequisite,invalid_params}; initial/repeat/partial success; disabled-template, malformed-JSON, missing-prerequisite failures with checked rollback. Target initial/repeat execution passed; exact params/recipient/action/channel checks and UTF-8 HEX decoding passed. WeCom channel enabled, ADMIN application enabled, credential fields present; no secrets logged.
- Evidence / scoped preflight backups: C:\Users\EDY\AppData\Local\Temp\course-notify-config-20261013145148 (preflight.json, two empty scoped before SQL exports, postconditions.tsv). Source hash recorded there. Existing target configuration was absent, so recovery would disable only these new rules after review.
- Dependency/integration impact: no code change, dependency, permission assignment, channel credential, preference, service restart or version-ledger mutation; configuration reads are uncached by rule/template ID.
- Remaining work: actual authenticated send and WeCom external-delivery acceptance not executed to avoid messaging employees. No remote deployment performed. Controlled fixture databases retained, no destructive cleanup.
- Ownership: released on delivery.
