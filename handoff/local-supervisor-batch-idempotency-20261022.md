# local-supervisor-batch-idempotency-20261022

- Environment: local (ZSJOS_AGENT_ENV unset; environment marker absent).
- Owner: Codex /root; branch main; worktree D:/ZSJ-OS; base 404387de566fcb9abf4b264f0e005bf0566ea209; integration None.
- Goal: prevent supervisor batch item keys exceeding the existing 40-character persistence limit while preserving retry identity and compatible historical keys.
- Non-goals: database/schema/history changes, permissions, frontend behavior, service restart/deployment, dependencies or Git publication.
- Ownership: this record; SubordinateSalesServiceImpl.java and SubordinateSalesServiceImplTest.java under backend/yudao-module-zsjos; docs/api/zsjos-subordinate-sales.md (idempotency paragraph only). Preserve existing documentation edits; previous subordinate UI workstream released ownership.
- Dependencies: existing Hutool SHA-256, command fingerprint/replay and independent item transactions; unchanged API consumers in Workbench and Admin.
- Verification: focused service/command/transaction/mapper tests and reactor compilation; regression for UUID and maximum-length request keys, four-digit and maximum Long IDs, deterministic retries, different items/batches and historical short keys; scoped diff checks.

## Delivery

- Changed batch item key derivation only: retain `batchKey:leadId` up to 40 characters; otherwise use the first 40 hexadecimal characters of its SHA-256 digest. All five supervisor batch actions share this path. No API, authorization, transaction, schema or data changes.
- Historical short keys and completed batch replay remain compatible. Already completed failed batches retain their saved response; users must start a new batch after backend deployment. Workbench opens each batch dialog with a new key and clears it after receiving the result.
- Inspected both consumers: Workbench api.batchSupervisorLeadAction and Admin workbenchMenus batch release/transfer retain identical request/response contracts; no frontend source changes or frontend runtime acceptance claimed.
- Maven reactor BUILD SUCCESS: 26 tests passed, zero failures/errors/skips across SubordinateSalesServiceImplTest (17), SubordinateSalesCommandServiceTest (7), SubordinateTransferTransactionTest (1), SubordinateSalesCommandMapperSqlTest (1). New parameterized cases verify 36/40-character batch keys, IDs 999/1000/Long.MAX_VALUE, stable repeat derivation, distinct batch/item keys and the exact 40-character compatibility boundary. Existing transfer and completed-replay tests pass.
- Evidence: C:/Users/EDY/AppData/Local/Temp/zsjos-supervisor-batch-idempotency-test.log. Scoped git diff --check passed. Existing dirty documentation and unrelated work preserved.
- Limits: no live authenticated API/browser acceptance; no database writes, service restart, deployment, dependency addition, commit or push. Backend must be deployed/restarted separately for runtime behavior to change.
- Ownership released.
