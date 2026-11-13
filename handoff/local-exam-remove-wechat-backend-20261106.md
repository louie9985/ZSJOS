# Exam collection without WeChat field

- Environment local, branch main, worktree D:/ZSJ-OS, base 7a7cf5e14b. Owner /root/backend_acceptance under /root coordination.
- User explicitly removes WeChat from exam collection. This supersedes the earlier four-required-fields contract.
- Scope examcollection field policy, public/task live definitions, submission filtering, archive application, focused tests and docs/api/exam-material-collection.md. Frontend owned by frontend_acceptance.
- Preserve stored submissions/archive history and existing archive capacity. No database mutation or migration. Preserve concurrent registration/studentcontact work.
- Verification isolated current-source Java compilation/tests and scoped diff. No commit authorized. Integration target/order None.

## Delivery
- New tasks contain name/mobile/id_card plus selected materials. Shared liveFields projection removes legacy wechat from existing task details, create replay and public runtime definitions without rewriting persisted snapshots.
- New submissions strip stale-client wechat before validation, normalized retry hashing and answer persistence. Previously submitted commands can still replay their original historical fingerprint. Archive application excludes wechat from selected/written keys and incoming values, retaining current archive values and immutable original submissions.
- Existing archive 34-field capacity and historical display remain intact. No database migration or data rewrite required.
- Added regression coverage for issued-task runtime filtering, stale-client submission and retry without wechat, historical fingerprint replay, and preservation of existing archive/raw-history values. Updated new-task field-count expectations.
- Current-source isolated compiler and 115/115 Java tests passed. Scoped diff check passed. Frontend behavior is owned and verified separately by frontend_acceptance. Real deployed HTTP behavior requires the updated backend runtime, which was not restarted by this workstream.
- Backend ownership released after this delivery. No staging or commit performed.
