# Registration
- Environment local (environment variable and environment file absent); owner Codex /root; main; D:/ZSJ-OS; base ba3e687e678ce92b67f16615244e47007d0892d4; integration None.
- Goal: all LeadDO timestamp sorting and read-only scan of other pages. Non-goals: other page changes, SQL, deployment.
- Ownership: LeadInboxSortControls.tsx, services/api.ts LeadSortField, LeadManagementPageReqVO.java, LeadMapper.java sorting switch, docs/testing/lead-time-sort-audit.md, this record.
- Preserve existing edits; previous mapper workstream delivered. Dependencies: existing paginated sort protocol. Verification: typecheck, static timestamp coverage and browser fixture when available.

## Delivery
- All 20 Lead timestamps exposed; 5 new server whitelist/mapper mappings; preserved existing dirty changes. Other pages scanned read-only; findings in docs/testing/lead-time-sort-audit.md.
- Passed typecheck, static timestamp coverage, scoped diff check and Chrome fixture desktop/mobile. Backend compile/tests and real API/database sorting unverified. No deployment, service changes, SQL, commit or push. Ownership released.
