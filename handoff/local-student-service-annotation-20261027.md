# 学员课程服务批注

## Registration — 2026-10-27
- ID: local-student-service-annotation-20261027; environment: local (ZSJOS_AGENT_ENV and /etc/zsjos/agent-environment absent).
- Goal: 学生交付中心首联清单下方紧凑批注卡片；三个字段按课程服务分别保存、自动保存、学习规划师和有管理范围的交付主管可维护。
- Non-goals: 学员备注栏、销售页面改版、服务生命周期/任务自动变更、角色授权、部署、提交、新依赖。
- Branch: main; worktree: D:\ZSJ-OS; base: 8279212fb97129ba6b006dbb0452ad843dfde4b5.
- Owner: current Codex chat. Target branch / integration order: None.
- Ownership: new StudentServiceAnnotation* Java files in registration controller/vo/service/DAL and focused tests; frontend/workbench/src/components/StudentServiceAnnotationCard*; frontend/workbench/src/services/studentServiceAnnotation*; additive slot in LeadDetailOverview.tsx and call site in RegistrationPages.tsx; new annotation browser fixture/test; V352__student_service_annotation.sql and focused SQL test; migration README; docs/api/student-service-annotation.md; frontend/workbench/docs/ui-guidelines.md; this handoff.
- Dependencies: existing student-service ownership/read policy, delivery-class data scope, System infra_boolean_string dictionary, MySQL 8.4 local container. Existing dirty changes preserved. No changes to other workstreams' implementation.
- Verification: focused service/authorization/snapshot/version tests; MySQL initial/replay/prerequisite failure/partial recovery/UTF-8 and no role changes; local scoped schema synchronization; browser desktop/mobile allowed/read-only/error/retry/conflict/student switching; sales overview unchanged; frontend static checks and backend compile as resources allow.
- User decision: annotations are per course service, not shared per person. Agent is free text as approved preview. Selection labels stored as snapshots; no history fabricated.

## Registration update
- Ownership includes feature-local StudentServiceAnnotationCard.css and script/student-service-annotation/verify_java.py. No new dependency; use existing Maven test classpath and isolated output.
- Existing supervisor views are read-only for other commands. User-confirmed supervisor annotation editing is an exception confined to this feature, with configured read/update buttons plus exact owner department management scope.
