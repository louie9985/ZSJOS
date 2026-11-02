# Media director and operator reassignment

## Registration — 2026-11-01T12:44:43+08:00

- ID: test-media-team-transfer-20261101; owner /root; environment test from /etc/zsjos/agent-environment; no variable override.
- Branch main; worktree /opt/zsjos; base 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425; target branch/integration order None.
- Goal: user requests 13 identified media-service director/operator assignments in four groups and explicitly confirmed both name discrepancies against the system identity. Director 31; target operators 43,46,522,59 according to fixed service mapping. Test zsjos-mysql-1/zsjos tenant 1 only.
- Scope: current service/account/group owners, active delivery-plan and waiting-stage directors, current confirmed-card owners, pending account diagnosis assignees. Preserve business state/content, past reviews and authors, teaching tasks/services, same-name other records and production tickets. No group creation for two currently ungrouped services.
- Ownership: this file; script/sql/mysql/repairs/transfer_media_team_20261101.py; docs/operations/media-team-transfer-20261101.md. Prior delivered operator-only files and unrelated build-info/recovery work untouched.
- Dependencies: enabled target users, four unique enabled director/operator relations, exact reviewed Person/service/account mapping; no active content BPM. Existing production tickets retain original owners and execution/review assignments.
- Verification: private complete scoped backup; MySQL same-schema temporary initial/repeat/failure rollback/partial recovery/stale rejection/compensating rollback; one guarded live transaction with system audit; original-plan zero-update replay; independent committed readback, field/history equality and UTF-8 HEX. Expected 255 mutations including 141 pending diagnosis tasks.
- Non-goals: personnel configuration, menu permissions, other students, teaching changes, historical workflow permissions or ticket transfer, notifications, DDL/migrations, deployments, Git operations.

## Delivery — 2026-11-01T12:47:03+08:00

- Context: owner /root; test/main /opt/zsjos; HEAD unchanged 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425; target branch/integration None.
- Goal achieved: 13 reviewed media services moved from director 47/operator 525 to director 31 with target operators 43/46/522/59 in groups of 3/4/3/3. User explicitly resolved both system-name differences before implementation.
- Changes: services 13, accounts 26, existing groups 11, active plans 26, waiting stages 26, confirmed-card current owners 12, pending diagnosis assignees 141; total 255 business row changes and 255 system audit events. No missing group/card was created.
- Preserved: teaching and same-name other services; student information; status/content; historical positioning submissions/authors; 13 completed interviews; 75 content batches; 44 production tickets and their execution/reviewer relations; teaching tasks. Prior delivered operator-only adjustment retained.
- Files: script/sql/mysql/repairs/transfer_media_team_20261101.py; docs/operations/media-team-transfer-20261101.md; this handoff. Other dirty/untracked work untouched.
- Evidence: syntax/UTF-8/whitespace/link checks passed. Same-engine temporary tables passed first application, duplicate no-op, injected rollback, committed partial recovery, subsequent-edit refusal and compensating rollback. Live full preimage lock/check, transaction, commit, zero-update original-plan replay and independent readback passed. Exact field/history comparisons passed; target identities and audit UTF-8 HEX passed; private evidence permissions passed.
- Private evidence: /opt/zsjos-runtime/backups/media-team-transfer-20261101-{verified,applied}; applied includes independent-verification.json. No private evidence published.
- Limits: no employee login/HTTP/browser business-command verification; historical content/ticket operational rights remain with their original recorded owners. Database relationships verified; frontend builds and full schema upgrade checks do not apply to this data-only maintenance.
- Dependencies/integration impact: None. No personnel configuration, permission, workflow engine, schema/migration, deployment, restart, notification, commit or push changes. Remaining work: None in authorized scope. Status: delivered; ownership released.
