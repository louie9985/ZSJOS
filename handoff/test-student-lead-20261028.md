# Test student Lead backfill 20261028

## Registration — 2026-10-28 15:03 Beijing

- ID/owner: `test-student-lead-20261028`, Codex sole writer.
- Environment: `test`, resolved from `/etc/zsjos/agent-environment`.
- Branch/worktree/base: `main`, `/opt/zsjos`, `0628f72f2c6208a6cc553f3f29b893de8f4ec697`.
- Goal: user-authorized single-student Lead creation from failed request 4731461, reusing internal Person 9514 and original submitting owner 27; complete self-sourced automatic business chain.
- Non-goals: general duplicate-rule changes, person/contact/service/order changes, notifications, permissions, migrations, deployments, branches or commits.
- Ownership: this record; `script/sql/mysql/repairs/backfill_student_lead_20261028.py`; `docs/operations/student-lead-backfill-20261028.md`; scoped target rows and private evidence under runtime backups.
- Dependencies: existing repair pattern read-only; current configured permissions/dictionaries/region/product/SKU/rule, and original request. Other dirty files belong to concurrent work and are preserved.
- Verification: MySQL controlled session fixtures, initial/repeat application, failure rollback including number counter, partial-state refusal and recovery, real-table rollback preview, committed readback, protected-row equality and UTF-8 HEX checks.
- Target branch/integration order: None.

## Delivery — 2026-10-28 15:00 Beijing

- Registration timestamp correction: the registration above was actually written at approximately 14:56 Beijing; 15:03 was a transcription error. Execution context and ownership were registered before implementation.
- Branch/worktree/HEAD: unchanged from registration.
- Goal and decision: user authorized this single existing-student backfill after diagnosis. Reuse Person and retain original request contact-field snapshot, selected course/SKU, dictionary and region; no next-follow-up time was supplied, so no reminder is invented. System repair actor 0 remains distinct from the original business owner.
- Result: committed at 14:59:33 Beijing, Lead `KZ202610281459330051`, valid and owned by original education submitter; one automatic follow-up, two completed lifecycle tasks, one open opportunity, frozen product/specification snapshots and traceable repair events. Sixteen business rows plus daily sequence advancement.
- Files: `script/sql/mysql/repairs/backfill_student_lead_20261028.py`, `docs/operations/student-lead-backfill-20261028.md`, this record.
- Verification: MySQL 8.4.11 controlled initial/repeat application; four failure rollbacks including sequence; partial-state refusal/recovery; actual-table rolled-back preview; committed readback and zero-write repeat; separate read-only connection checked unique Lead/opportunity, completed tasks, relation integrity, UTF-8 HEX, and exact equality of protected rows. Scoped static/content checks passed.
- Evidence: private runtime backups `student-lead-20261028-preview` and `student-lead-20261028-applied`; no sensitive payloads copied into repository.
- Impact: no Person/contact/service/order, permission, schema, version-ledger, service lifecycle, frontend or API changes; no notifications sent; existing unrelated changes preserved. Target branch/integration: None.
- Limits: browser/authenticated API and subsequent order/BPM flows unverified. General duplicate handling remains unchanged; the requested one-record creation is complete.
- Status: delivered; ownership released.
