# BPM and EAM Bootstrap Seed Workstream

- Workstream ID: `bpm-eam-bootstrap-seed`
- Goal: Seed tenant-1 EAM baseline categories and initialize existing recommended BPM models except the new Partner account open-request approval model.
- Non-goals: No BPM deployment or activation, no hardcoded approver or manager user IDs, no Partner open-request model seed, no real database execution, and no external permission or service changes.
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- Base commit: `e8674481cf5fabb2f9e0c0cfcc8ee9a952234f6a`
- Target branch: `main`
- Ownership scope: `script/sql/mysql/migrations/eam/V006__eam_category_baseline.sql`; BPM bootstrap seed SQL and bootstrap wiring; `script/sql/mysql/verify-bootstrap.sql`; directly affected BPM/database operational documentation; this handoff file.
- Owner: Codex `/root`
- Dependencies: Existing Flowable 8 model persistence schema, BPM versioned assets and manifest, tenant `1` bootstrap seed, and the completed file changes recorded by `partner-open-request`.
- Integration order: Apply after the current Partner open-request file changes; preserve its V177 and BPM asset registration while excluding that process key from the model seed.
- Verification plan: Validate model resource hashes and XML/JSON parsing; statically verify SQL ordering, foreign-key relationships, tenant ownership, idempotency, and exclusion of `zsjos_partner_open_request`; run bootstrap verification checks and `git diff --check`; execute against a controlled MySQL database only if an available environment is confirmed separately.
- Status: `in-progress`

## Delivery 2026-09-01 13:29:51 (Asia/Shanghai)

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `e8674481cf5fabb2f9e0c0cfcc8ee9a952234f6a`
- User goal: Change EAM asset-category initialization to tenant `1` and add all existing approval models to the fresh bootstrap except the newly added Partner account open-request approval.
- Key decisions: V006 seeds only the six requested root categories and their six custom fields under tenant `1`; the global EAM code rule remains tenant `0`. The fresh bootstrap seeds 11 unpublished tenant-1 Flowable models, explicitly excludes `zsjos_partner_open_request`, preserves existing tenant/key models, dynamically derives initial managers from enabled tenant-1 users, and does not create deployments, definitions, instances, or hardcoded approvers.
- Execution or analysis result: Added the BPM model seed after all Core and EAM prerequisites, bound four existing BPM forms dynamically, included the EAM Simple-model extra resource, updated bootstrap verification and directly affected operations documentation, and preserved the concurrent Partner open-request V177 work.
- Changed files: `script/sql/mysql/05-bootstrap-bpm-models.sql`; `script/sql/mysql/bootstrap.sql`; `script/sql/mysql/migrations/eam/V006__eam_category_baseline.sql`; `script/sql/mysql/migrations/eam/README.md`; `script/sql/mysql/verify-bootstrap.sql`; `docs/operations/database-migrations.md`; `docs/operations/feedback-management-release.md`; `docs/operations/lead-appeal-deployment.md`; `docs/operations/sales-order-dual-approval-deployment.md`; `docs/operations/zsjos-bpm-versioned-assets.md`; `handoff/bpm-eam-bootstrap-seed.md`.
- Verification evidence: Confirmed 11 model insert statements, 11 unique source resources, one EAM Simple extra resource, one exclusion-only occurrence of `zsjos_partner_open_request`, and seed ordering after EAM V011. All embedded BPMN/XML and JSON resources parsed; ten BPMN resources matched their repository files byte-for-byte; EAM generated BPMN/Simple JSON and all 11 metadata documents validated. `python -m unittest script.bpm.test_validate_manifest` passed 4 tests. `git diff --check` and focused trailing-whitespace checks passed, apart from existing line-ending warnings.
- Dependency or integration impact: Fresh bootstrap now requires an enabled tenant `1` and at least one enabled tenant-1 System user before BPM model seeding. Existing environments are unchanged and continue to use the documented manual import path. No dependency was added and no database or external service was changed.
- Remaining work: Controlled MySQL bootstrap execution and read-only schema/data comparison remain unverified because neither `mysql` nor `mysqlsh` is installed and starting Docker was not authorized. Full `script/bpm/validate_manifest.py` remains blocked by the pre-existing checksum mismatch for `zsjos_feedback_requirement_approval/1.0.0/process.bpmn20.xml`; the seed embeds the actual repository asset and the unrelated manifest was not rewritten. Status: `complete-with-external-verification-pending`.

## Delivery 2026-09-01 14:13:04 (Asia/Shanghai)

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `e8674481cf5fabb2f9e0c0cfcc8ee9a952234f6a`
- User goal: Recheck the tenant-1 EAM and BPM bootstrap work after overlapping workspace edits and restore any portions that were overwritten.
- Key decisions: Preserved all current migration-order and unrelated workspace changes. Restored only the missing BPM seed wiring and directly affected documentation; retained the existing 11-model seed, tenant-1 EAM category changes, verification SQL, and explicit exclusion of `zsjos_partner_open_request` without modifying that separate workstream's asset or manifest state.
- Execution or analysis result: Confirmed the EAM V006 tenant changes, BPM seed resources, and bootstrap verification were intact. Restored `05-bootstrap-bpm-models.sql` sourcing after EAM V011 registration and restored fresh-bootstrap versus existing-environment guidance in the EAM and BPM operational documentation.
- Changed files: `script/sql/mysql/bootstrap.sql`; `script/sql/mysql/migrations/eam/README.md`; `docs/operations/database-migrations.md`; `docs/operations/feedback-management-release.md`; `docs/operations/lead-appeal-deployment.md`; `docs/operations/sales-order-dual-approval-deployment.md`; `docs/operations/zsjos-bpm-versioned-assets.md`; `handoff/bpm-eam-bootstrap-seed.md`.
- Verification evidence: Static checks confirmed 11 model blocks and inserts, 11 unique source resources, one EAM Simple extra resource, one exclusion-only occurrence of `zsjos_partner_open_request`, tenant-1 EAM categories and fields, and seed ordering after EAM V011. All ten BPMN seed resources matched current repository assets byte-for-byte; the generated EAM BPMN and Simple extra JSON parsed successfully. `python -m unittest script.bpm.test_validate_manifest` passed 4 tests. `git diff --check` and focused whitespace checks passed with existing line-ending warnings only.
- Dependency or integration impact: Fresh bootstrap once again creates unpublished tenant-1 BPM models after Core/EAM initialization. Existing databases, deployments, definitions, process instances, permissions, and the concurrent Partner open-request work were not changed.
- Remaining work: Controlled MySQL bootstrap execution and schema/scoped-data comparison remain unverified because no database service was started. Full `python script/bpm/validate_manifest.py` remains blocked by the existing `zsjos_feedback_requirement_approval` checksum mismatch: manifest `4d9c6cb690e8cea86bcea7d31f5d09da772d4e82b4514e0d2cb1e210efc051cd`, current asset `4a6975ec06bbaa6f272278d8526f6cf56b28b9707f2b96270a9a89499671ae59`. Status: `restored-with-external-verification-pending`.

## Delivery 2026-09-01 (re-applied after user overwrite)

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `e8674481cf5fabb2f9e0c0cfcc8ee9a952234f6a`
- User goal: Reapply the tenant-1 EAM category initialization and BPM model bootstrap after the prior working-tree state was overwritten.
- Result: Restored the tenant-1 category/custom-field changes, restored BPM seed wiring including V177 prerequisite ordering, and retained the 11-model seed with `zsjos_partner_open_request` excluded.
- Verification: Confirmed all six EAM roots and six fields use tenant `1`; bootstrap contains V177 before the model seed; the seed contains 11 model insert statements and only one exclusion-only occurrence of `zsjos_partner_open_request`. No unrelated user files were changed by this reapplication.
- Remaining work: Real MySQL execution remains unverified because no MySQL client is installed; no external service was started.
