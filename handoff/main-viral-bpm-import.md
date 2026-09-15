# Workstream: main-viral-bpm-import

- Goal: deliver two importable SIMPLE material approval models with BPM-owned forms and fixed business process keys.
- Non-goals: delivery/contact extensions, other media processes, content batch review, deployment, database deletion.
- Branch/target: main
- Worktree: D:\ZSJ-OS
- Base: e78a02f0ed4eceb9975bf92fb428cc19585c8a16
- Owner: current Codex thread
- Status: implementing
- Current scope: extract standalone FormCreate rendering-rule arrays and option objects under script/bpm/forms/viral-review, with usage instructions; do not change runtime or existing versioned models for this extraction.
- Dependencies: existing BPM import/form services and material version submission.
- Integration order: BPM import extension, material adapter, assets, Admin configuration, tests/docs.
- Ownership: material service/type VO and Admin material API/page; BPM model save VO/import service and their tests; script/bpm new viral assets/manifest/validator; directly affected deployment documentation; this handoff.
- Verification: focused Java import/submission tests, asset validation, Maven compilation, Admin checks and browser verification when available. Preserve all unrelated working-tree changes.

## Delivery 2026-09-14 18:30:00 Beijing
- Branch: main; Worktree: D:\ZSJ-OS; HEAD: e78a02f0ed4eceb9975bf92fb428cc19585c8a16
- User goal: provide one-click importable viral-account/content BPM models and remove business-side approval binding, while retaining student delivery extension scope.
- Key decisions: added optional embedded BPM form creation to model import; viral material types route by stable server mapping; legacy configurable bindings remain for non-viral types; imported assets require tenant role configuration and never carry role IDs.
- Execution result: implemented BPM import extension, fixed routing and snapshot variables, hid viral binding UI, added two SIMPLE assets and manifest entries, and updated deployment guidance.
- Changed files: backend/yudao-module-bpm/.../BpmModelSaveReqVO.java; backend/yudao-module-bpm/.../BpmModelServiceImpl.java; backend/yudao-module-zsjos/.../MaterialApprovalContract.java; backend/yudao-module-zsjos/.../MaterialService.java; backend/yudao-module-zsjos/.../MaterialTypeServiceImpl.java; frontend/admin/src/views/zsjos/materialType/index.vue; script/bpm/zsjos_viral_account_review/1.0.0/process-model.json; script/bpm/zsjos_viral_content_review/1.0.0/process-model.json; script/bpm/manifest.json; script/bpm/validate_manifest.py; docs/operations/viral-material-review-deployment.md.
- Verification evidence: `python script/bpm/validate_manifest.py` passed with 24 versioned assets. Maven/Admin checks and live import were not run.
- Dependency/integration impact: BPM import now may create a tenant-local form when `importForm` is present; requires `bpm:form:create`. No dependency or database schema change. Existing non-viral type binding remains backward compatible.
- Remaining work: configure tenant 1 role in each imported approval node, publish/enable models, and validate a real submission and status callback.

## Delivery 2026-09-14 18:25:00 Beijing
- Branch: main; Worktree: D:\ZSJ-OS; HEAD: e78a02f0ed4eceb9975bf92fb428cc19585c8a16
- User goal: complete confirmed viral account/content and student extension approval scope.
- Key decisions: added importable SIMPLE viral assets with tenant-local embedded forms; fixed server routing; hid viral business binding UI; preserved non-viral compatibility.
- Execution result: completed code, assets, tests, and deployment docs. No tenant database or BPM publication executed.
- Changed files: BPM model VO/service/test; ZSJOS material service/type/contract/test; Admin material type page; two BPM assets; manifest/validator; BPM deployment docs; this handoff.
- Verification evidence: BPM import test passed; ZSJOS focused material test passed; manifest validation passed with 24 assets; scoped Admin ESLint passed. Full Admin typecheck has unrelated existing errors; live import/publish unverified.
- Dependency/integration impact: no new dependency or schema migration. Importing embedded forms requires `bpm:form:create`; role placeholder must be replaced in BPM before publish.
- Remaining work: import both JSON files in tenant 1, configure valid role and publish/enable, then perform controlled approval submissions.

## Delivery 2026-09-14 18:45:20 Beijing
- Branch: main; Worktree: D:\ZSJ-OS; HEAD: 436ba84cd8ab5471356e3faa62db9ab44e3c76e9
- User goal: standard-compatible independent rendering rules and form configuration JSON.
- Key decisions: extract existing five read-only business snapshot fields as actual JSON objects/arrays; no importForm requirement and no invented form IDs.
- Execution result: generated two rules files, two options files and designer instructions under script/bpm/forms/viral-review.
- Changed files: script/bpm/forms/viral-review/viral-account-rules.json; viral-account-options.json; viral-content-rules.json; viral-content-options.json; README.md; this handoff.
- Verification evidence: all four files parsed and round-tripped as UTF-8 JSON; both rules arrays contain five unique read-only fields. No live designer rendering verification.
- Dependency/integration impact: no runtime changes, dependencies or external writes; current HEAD changed since earlier work, no branch or commit operation performed by this turn.
- Remaining work: paste into designer, save and select tenant-local forms in models; live rendering and business submission verification pending.
2026-09-14 19:25 Beijing: added production-content-review standalone FormCreate rules/options JSON and README; parsed JSON successfully; no runtime or external changes.
