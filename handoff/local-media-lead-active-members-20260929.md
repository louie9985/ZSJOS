# Workstream — media-lead-active-members-20260929

## Registration — 2026-09-29 Beijing time
- Environment: local (ZSJOS_AGENT_ENV and /etc/zsjos/agent-environment absent); branch main; absolute worktree D:/ZSJ-OS; base/HEAD 5fadfb3e9be563ac4cb3e5c142308549944aa465. Owner Codex /root. Target branch/integration order None.
- Goal: remove disabled accounts from the new-media dashboard team-member progress table while retaining their historical leads in team totals.
- Non-goals: change historical attribution, lead totals, target settings, permissions, database rows, shared service state, or Git state.
- Ownership: backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/performance/MediaLeadAnalysisService.java; matching MediaLeadAnalysisServiceTest.java; docs/api/media-lead-analysis.md; this uniquely owned handoff file. Preserve all unrelated work.
- Dependencies: reuse MediaLeadAccess.mediaUsers(), which already filters to enabled eligible accounts. Verification: focused Java service test, strict UTF-8 and scoped whitespace/content checks. Live authenticated API behavior requires a deployed backend.
- Continuation note: this record was written after the first scoped code edit in this follow-up turn; no overlapping writer or unrelated file was changed.

## Delivery — 2026-09-29 15:14 Beijing time
- Context: local/main/D:/ZSJ-OS; HEAD 5fadfb3e9be563ac4cb3e5c142308549944aa465 unchanged from registration; owner Codex /root. Target branch/integration order None.
- User goal/result: the team-member progress list is now built only from the existing enabled, eligible media-user roster. Historical lead contributors absent from that roster, including disabled accounts, no longer reappear as member rows. Their historical lead records remain included in authorized team totals and period statistics.
- Key decision: keep the existing MediaLeadAccess.mediaUsers status/post filter as the source of truth; change only member-row construction, not the lead fact query or attribution rules.
- Changed files: MediaLeadAnalysisService.java; MediaLeadAnalysisServiceTest.java; docs/api/media-lead-analysis.md; this handoff record.
- Verification: focused Maven reactor command exited 0; MediaLeadAnalysisServiceTest 12 tests passed with zero failures/errors/skips. New regression proves only the enabled user appears while yesterday's team total still includes both active and disabled contributors. Three edited source/test/doc files and this record decode as strict UTF-8 with no replacement characters or trailing whitespace; scoped git diff --check passed. No frontend bundle changed, so browser/build verification does not apply to this server-only response change.
- Dependencies/integration: no new dependency, API schema, schema/data, grant, shared service lifecycle, or Git operation. Workbench consumes the existing members array and needs no client change.
- Remaining: running shared backend is not restarted/deployed by this turn. Authenticated live-data verification of disabled accounts awaits the updated backend runtime.
