# Test order finance workflow publication — 2026-11-19

## Registration

- Workstream: `test-publish-order-finance-20261119`; owner: Codex.
- Goal: publish `zsjos_sales_order_finance_approval` from asset `3.0.0` in the test environment through the authorized BPM model API.
- Non-goals: backend deployment/restart, permission changes, order approval, legacy instance migration/termination, historical asset repair.
- Environment: `test`, resolved from `/etc/zsjos/agent-environment`.
- Branch/worktree: `main`, `/opt/zsjos`; base: `82b5e11532bd13cb7965f80edd436a8a9eb25cdc`.
- Ownership: this record; private prepared import JSON and scoped preflight evidence outside Git. No source/model-asset edits planned.
- Dependencies: completion of the separately running backend deployment; a legitimate authenticated BPM operator with import/deploy permission. No changes to that deployment are owned here.
- Target branch / integration order: None.
- Verification: validate the finance asset/hash; confirm tenant category/form and absence of a same-key model; capture legacy definition/instance state; after publication verify new enabled definition, SIMPLE task/candidate/form metadata, UTF-8 text, and preservation of legacy running instances. A real order submission is outside this operation.

## Preflight

- Asset: `script/bpm/zsjos_sales_order_finance_approval/3.0.0/process-model.json`.
- SHA-256: `78aec3e61581a14af3134e90aed29d06990725178eb958676f7824105069e1ca`.
- Current API contract and asset require finance-only approval. Older dual-approval deployment documentation is historical and conflicts with the current target; this conflict was reported before any publication.
- New process key is separate from `zsjos_sales_order_dual_approval`. Preserve all four observed running legacy instances without migration.
- Import must bind the enabled tenant-1 existing order information form (ID 13) after rechecking it, and category `general-module`. The form ID is environment-specific and must not be copied into the portable asset.
- Use `POST /admin-api/bpm/model/import` (`bpm:model:import`), followed by `POST /admin-api/bpm/model/deploy` (`bpm:model:deploy`); the importer becomes the model manager. Do not write Flowable tables directly.
- Finance asset validation passed independently. Full-manifest validation is blocked by existing unrelated historical dual-approval checksum and media-account-delete asset-format errors; neither is modified here.
- Backend was inactive while a separate deployment was building. Publication has not been attempted. Operator/session clarification is pending; no tokens are collected from persistence and no session is fabricated.

## Prepared result and pending execution

- User confirmed that the backend deployment is theirs and designated `LiJunCheng` as publication operator. The enabled tenant-1 account and its configured `bpm:model:import`, `bpm:model:query`, and `bpm:model:deploy` permissions were verified read-only; live session permission checks remain required.
- Restricted preparation directory: `/tmp/zsjos-publish-order-finance-20261119-3byhwlj1` (directory mode 0700; files 0600). It contains the scoped pre-state, environment-bound import JSON and a one-shot API client. No credentials are included.
- Prepared import SHA-256: `65917a595231a330e06d966546969c0b3c1e84d6173d9ed2f09529f03f83ae3f`. The sole change from the portable asset is the verified test-tenant form binding `formId=13`; portable asset and manifest remain unchanged.
- Rechecked before preparation: no finance-key model, enabled category and order form, four running legacy instances. Finance asset validation and API-client syntax check passed.
- Last observed deployment process had exited, but `zsjos-backend.service` remained inactive with PID 0 and the release symlink unchanged. This workstream did not start, stop or restart any service.
- No model import/deploy request has been sent. Runtime publication and post-publication checks are **unverified and pending** backend availability and legitimate login credentials. The user was asked to provide a restricted credential-file path without sending a password in chat.
- Concurrent deployment generated changes to frontend build-info files; these are outside this workstream and were preserved. Only this record was added to Git's working tree by this workstream; no stage/commit/push.
- Status: preparation delivered, publication pending external prerequisites. File ownership released until execution resumes.

## Execution resumed — 2026-11-20

- Same workstream and ownership scope resumed on explicit user instruction to publish now. Environment, branch and base commit are unchanged.
- Backend is active and `/actuator/health` returns HTTP 200. Rechecked: finance model/definition still absent, category/form/operator enabled, four legacy running instances still present.
- Existing unrelated deployment/migration-tool and build-info changes are preserved. No service operation or permission change is needed.
- Authentication is still pending the previously requested legitimate credential/session file; no password guess, token extraction from database or authentication bypass will be used.
- Resume verification: finance asset validator passed again; backend health HTTP 200; no same-key finance model/definition; enabled form 13 and `general-module` category; enabled designated operator 39. Fresh private pre-state contains four running legacy instances and twelve pending tasks.
- The private API client and read-only post-publication verifier are prepared and passed Python syntax checks. Publication, model/form metadata, deployed BPMN and UTF-8 HEX checks, and before/after legacy-state comparisons are ready to execute after login.
- No credential/session-file path has been received. No login/import/deploy was attempted, and the workflow remains unpublished. Backend availability is no longer a blocker; legitimate authentication is the sole remaining prerequisite. No runtime state was changed by this resumption. File ownership released pending that input.

## Authenticated publication execution

- User supplied the designated account's password and explicitly instructed password login. Same workstream ownership resumes for this record and private execution/verification evidence. Credentials are not recorded in repository files or evidence.
- Proceed through the existing authenticated BPM import/deploy APIs, then compare deployed metadata and the legacy instance/task pre-state. No service restart, permission changes or real order submission.

## Publication delivered — 2026-11-20

- Successfully logged in as the designated `LiJunCheng` account, verified live permissions, imported the new finance model and published through the existing BPM APIs. The temporary login session was logged out after execution; credentials and tokens were not saved.
- Process key: `zsjos_sales_order_finance_approval`; semantic asset version: `3.0.0`; SHA-256: `78aec3e61581a14af3134e90aed29d06990725178eb958676f7824105069e1ca`.
- Model ID: `d6924b36-e434-11f1-a572-da382807cbc0`.
- Deployment ID: `d721b9f9-e434-11f1-a572-da382807cbc0`.
- Definition ID: `d8452b5c-e434-11f1-a572-da382807cbc0`; Flowable version: **1**, enabled. Semantic version 3.0.0 and Flowable version 1 refer to different version systems.
- Publication timestamp: `2026-11-20 00:18:24` (Asia/Shanghai, definition metadata); scoped verification completed at `2026-11-19T16:18:51Z`.
- Verification passed: single enabled new definition; SIMPLE type 20; existing tenant form 13 and category `general-module`; designated model manager; exact portable graph fields preserved, including `financeReview`, strategy 35, or-sign method 3 and enabled native add-sign button. Deployed BPMN user-task keys are `StartUserNode` and `financeReview`, with candidate strategy 35 on the finance task. Chinese definition name was checked through the UTF-8 database connection and compared against `HEX(NAME_)`.
- Before/after comparison passed for both legacy definitions, all four running legacy instances, all twelve legacy pending tasks and the unchanged form. No legacy migration, termination, approval, permission change or service operation occurred.
- Private evidence: `import-result.json`, `imported-model.json`, `deploy-result.json`, `published-model.json`, and `verification.json` in the registered restricted preparation directory. The verifier was corrected to normalize the existing scalar manager-ID storage format; the subsequent full scoped verification passed.
- Runtime business submission/approval was not exercised because this operation publishes the definition and does not create real orders. Full-manifest historical validation issues remain outside scope as recorded above.
- Status: **delivered**. Record ownership released; unrelated worktree changes preserved. No stage, commit or push.
