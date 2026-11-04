# 考试资料收集开发记录

## Registration — 2026-11-04 (Asia/Shanghai)
- Environment: local; ZSJOS_AGENT_ENV unset, environment marker absent.
- Owner: Codex /root, chat 01a1a550-9419-7f10-8abd-ed383311dd13.
- Branch: codex/exam-material-collection; worktree: D:/ZSJ-OS-exam-material-collection; base: f6712f0beb.
- Goal: implement the user-approved exam collection design and UI acceptance criteria.
- Non-goals: main runtime/database changes, deployments, real role grants, unrelated refactoring, new dependencies, automatic merge/push.
- Ownership: exam-collection backend Controller/VO/Service/DAL/tests; directly required studentinfo archive/summary integration; task/notification scene integration; Workbench exam pages/services/routes/home reminder/archive/summary integration and scoped styles/tests; public collection frontend; Admin unsupported-menu filtering; scoped migration/verification scripts; directly affected architecture/API/UI documentation; this record.
- Dependencies: existing System permissions/dictionaries/notification APIs, Infra file storage, ZSJOS generic business-task command/scene facilities, existing archive revisions and collection configuration. No new dependency approved.
- Verification: focused Java behavior/auth/concurrency tests; isolated MySQL migration first/repeat/failure/recovery checks; frontend types/build/tests; browser desktop/mobile UI-01 through UI-12; both frontend menu consumers; main resource isolation.
- Target branch/integration order: None (feature delivery first; merge not authorized).
- Authorization: user explicitly requested baseline commit before new branch, independent worktree plan accepted, and goal execution on new branch.

## Progress — 2026-11-04 (Asia/Shanghai)
- Committed completed existing main changes as f6712f0beb (16 files). Existing handoffs record 37 collection, 135 order, 24 attendance tests passed; reused unchanged evidence and checked staged diff. Temporary files/build caches excluded and preserved.
- Created independent branch/worktree from that commit. Main remains checked out in D:/ZSJ-OS; no database or service operations performed.
- Current implementation concern: archive revisions are form-bound, while exam collection may target services without an existing form. Design must provide service-scoped archive integration without rewriting original order submission snapshots or bypassing existing permissions.
- Remaining: maintained design/UI acceptance document, concrete archive integration contract, implementation and full scoped verification. Goal remains active.
