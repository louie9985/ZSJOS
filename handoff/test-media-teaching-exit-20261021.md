# Registration — test-media-teaching-exit-20261021

- Environment test from /etc/zsjos/agent-environment; owner Codex /root; branch main; worktree /opt/zsjos; base 404387de566fcb9abf4b264f0e005bf0566ea209; integration None.
- User explicitly authorized execution after reviewing the exact two-service plan. Target: zsjos-mysql-1 / zsjos / tenant 1, service IDs 1111 and 1112. Change only teaching_delivery true to false; preserve every other service field and media association. No deletion, class unlink, state transition, permission change, restart or deployment.
- Ownership: this record; script/sql/mysql/repairs/exit_teaching_delivery_20261021.py; docs/operations/media-teaching-exit-20261021.md; private evidence under /opt/zsjos-runtime/backups/media-teaching-exit-20261021-test. Preserve all existing dirty files; use unique record to avoid concurrent default handoff writes.
- Dependencies: V336 installed and running backend contains participation predicates; exact target identities and active/accepted director/operator relations verified before execution. No new dependency.
- Verification: actual MySQL temporary-table initial/replay/injected-failure rollback and recovery; private before-image; locked identity/service revalidation; transactional whole-row comparison with only flag changed; linked media records unchanged; independent committed readback, teaching exclusion and media responsibility predicates; zero-write replay. Browser/authenticated API checks separately reported if unavailable.

## Delivery

- Executed authorized exact two-row update on test tenant 1: services 1111/1112 teaching_delivery true -> false. All other fields, including status/acceptance/class/version/update_time/name and director/operator, preserved. No deletions or media transitions.
- Actual MySQL temporary fixtures passed injected rollback, recovery/initial application, whole-row preservation, replay. Independent committed readback passed exact service after-images and full equality of captured related records; teaching scope 0, media responsibility scope 2, UTF-8 name HEX unchanged. Exact plan replay committed 0 updates.
- Evidence: /opt/zsjos-runtime/backups/media-teaching-exit-20261021-test/{plan.json,verification.json}; directory0700/files0600. Plan SHA256 f235516aaba2f7a07d77615ce9d6121442738a3866acd5edc2f41adc6b6e7b3c. Private record contents not printed or committed.
- Initial temporary DDL same-name LIKE limitation resolved using SHOW CREATE TABLE. First actual apply stopped before update because empty tuple results differed from decoded JSON lists; confirmed all business rows unchanged, normalized query result container, reran fixtures and committed successfully. No guard bypass.
- Changed files only this handoff, dedicated repair script and operation document. Existing dirty/untracked work untouched. No dependency, migration, ledger, permission, notification, deployment, restart or Git operations.
- Authenticated API/browser interaction not performed; verified database state and query predicates, not end-to-end UI acceptance. Ownership released.
