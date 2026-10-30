## Registration — local-h5-supplement-attachment-20261030
- Environment local (variable and environment file absent); owner Codex /root; branch main; worktree D:/ZSJ-OS; base 208abf277065706cbee45164113903eded9e6303; integration None.
- Goal: distinguish partner business identity from upload account when supplementing leads. Non-goals: database, frontend contract, deployment, unrelated work.
- Ownership: LeadSubmitterActionService.java, PartnerAppPortalController.java, LeadSupplementRemarkTest.java, docs/api/partner-app-api.md, this record.
- Dependencies: existing partner context and attachment ownership validation. Verification: focused Maven tests and compile, scoped diff. Preserve other workstreams.

## Delivery
- Corrected partner supplement controller/service to carry authenticated account ID separately from partner business ID. Lead ownership and event subject remain partner-based; file validation remains account-based. ADMIN behavior and HTTP payload unchanged. API documentation synchronized.
- Maven reactor compilation passed. LeadSupplementRemarkTest: 10/10 passed; LeadAttachmentServiceImplTest: 4/4 passed. Additional LeadSubmitterActionServiceTest: 7/9 passed; two failures in unchanged requestAssist historical-owner fallback/missing-owner behavior, outside this fix. Combined command exit 1; no claim of full suite success.
- Scoped diff/content checks passed. No live HTTP/account acceptance or deployment performed; production behavior remains unverified until rollout. No database/service/Git publication operations. Ownership released.
