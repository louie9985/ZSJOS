# Review fixes — 2026-10-14

## Registration
- Environment: local (ZSJOS_AGENT_ENV and /etc/zsjos/agent-environment absent).
- Workstream: local-review-fixes-20261014; owner: current Codex chat /root.
- Goal: implement the approved twelve review findings in six groups: safe lazy review attachments, BPM next-assignee scope, service-based positioning reads, batched course/points attachment reads, calendar concurrency/Admin links, controlled relation SQL and deployment test entry.
- Non-goals: live data repair, historical migration rewrites, role assignments, dependency additions, service operations, commits/pushes/branches; preserve unrelated files and historical approval snapshots.
- Branch: main; worktree: D:/ZSJ-OS; base: e07efc9d6baa5d40b5ec07b78f6743b0d1b2915e; integration: None.
- Ownership: AttachmentCard/contentReviewStudent/MediaStudentReviewContext/PointsAttachments/CalendarColorEditor and supporting focused service utilities/tests; BPM BpmTaskServiceImpl and scope tests; Infra FileApi/FileApiImpl/FileService/FileServiceImpl batch metadata API/tests; ZSJOS ContentReviewStudentService/Controller, PositioningCardService, CourseAttachmentService/CourseCalendarEventService, personal-calendar service/mapper and focused tests; Admin course-calendar API/page and notification handlers/helper/tests; script/sql/mysql/tools/repair_relation_1016.sql and verification, test_deploy_preflight.py; directly affected docs and this handoff.
- Dependencies: existing System/Infra/BPM public APIs and available frontend facilities; no new packages. Source targets have no pre-existing edits.
- Verification: focused Java/reactor tests, TS checks, scoped browser fixtures at desktop/mobile, frontend builds to isolated outputs, controlled MySQL first/replay/failure tests without real-target writes, final scoped OCR and manual validation.

## Scope update
- Include CalendarCreatorNamesTest for changed batch API; PersonalCalendarColorMapperTest, FileServiceImplTest and dedicated review-fixes browser fixtures. Admin route identity verified from V191 componentName ZsjosCourseCalendar; no menu/role writes. SQL fixtures use retained review1016_* schemas only.
