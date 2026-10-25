# 公海正式转派发起人推进修复

## Registration — 2026-10-23
- ID/owner: local-transfer-starter-repair-20261023 / Codex.
- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Branch/worktree/base: main; D:/ZSJ-OS; 404387de566fcb9abf4b264f0e005bf0566ea209. Target/integration: None.
- Goal: prepare minimal model correction and recovery for tenant 1 transfer requests 1 and 2.
- Non-goals: no publication, runtime recovery, service restart, grants or unrelated data changes.
- Ownership: script/bpm/prepare_transfer_model_repair.py; script/bpm/test_prepare_transfer_model_repair.py; script/bpm/transfer_recovery_preflight.py; script/bpm/test_transfer_recovery_preflight.py; script/bpm/maintenance/TransferMaintenanceRunner.java; backend/yudao-module-bpm/src/test/java/cn/iocoder/yudao/module/bpm/service/task/BpmTransferStarterRecoveryTest.java; docs/operations/lead-transfer-starter-repair.md; this record. Any proven generic callback fix requires ownership update first.
- Dependencies: existing recovery helper, existing model services and frozen reviewer contract.
- Verification: offline generator/preflight tests, real Flowable/H2 recovery and callback tests, BPM/ZSJOS focused checks, read-only MySQL preflight; two frontend contract inspection.

- Ownership update: script/bpm/maintenance/TransferMaintenanceAgent.java (scoped loader for the reviewed transfer runner; no attach executed).

- Ownership update: backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadTransferResultRegressionTest.java (reject/cancel/duplicate terminal events).

## Delivery — 2026-10-23
- Prepared minimal model correction preserving all current model metadata and buttons. Local TEMP/zsjos-transfer-starter-20261023 holds before/update/revision and per-target read-only reports; exact candidate-only payload equality checked.
- Added scoped transfer maintenance runner/agent using existing model service and recovery helper. Full source compiled with current reactor classes/dependencies. Requires named permitted model manager, exact target whitelist, unblocked report, model comparison, rollback rehearsal, corrected publication, frozen reviewer/business checks and before-commit owner/state recheck. No attach or runtime mutation executed.
- Python generator/preflight: 6 tests PASS. BPM reactor compilation and 7 real Flowable/H2 tests PASS, including actual Spring afterCompletion -> proxied approveTask with real comment persistence. Old missing-expression failure reproduced; corrected candidate strategy reaches the frozen reviewer. Generic transaction code unchanged.
- Recovery tests verify same-instance/audit preservation, repeat idempotence, tenant/permission/frozen reviewer failures, postcondition rollback and business-lock rollback.
- ZSJOS reactor verification BLOCKED before tests by existing service/zsjkqt/ZsjkqtService.java:226 use of removed JsonNode.isContainerNode(). Added reject/cancel/duplicate result test remains unexecuted; existing approval test also not run. Did not modify unrelated source.
- Read-only tenant-1 preflight: request 2 CANDIDATE; request 1 BLOCKED because current Lead owner differs from from_owner_user_id (now already requested owner). Do not restore or reassign it without separate business decision.
- Both frontend consumers inspected: Workbench BpmApprovalCenterPage/BpmApprovalDetail and Admin BPM todo/process-instance detail consume existing BPM task/detail state. No frontend changes. Authenticated two-frontend browser acceptance is pending publication/recovery.
- UTF-8, Python syntax, scoped whitespace and reviewed payload checks PASS. No DB changes, publication, service restart, permissions, commit or push. Target-engine rehearsal/publication/recovery require explicit authorization and user-designated audit operator per repository section 4. Ownership released; runtime follow-up may reuse this registration with updated execution scope.
