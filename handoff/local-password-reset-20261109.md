# Local user password reset 2026-11-09

## Registration
- Environment local, environment variable and environment identity file absent.
- Owner Codex /root, workstream local-password-reset-20261109, branch main, worktree D:/ZSJ-OS, base references current HEAD at execution.
- Goal user-authorized reset of all local tenant 1 user passwords, including system_users and zsjos_partner_account.
- Scope this record, unique scoped password-hash recovery backup, password columns and update_time of the two target tables.
- Non-goals permissions, account status, other tenants, migrations, source changes, publication.
- Dependencies existing BCrypt configuration and local MySQL. Integration None.
- Verification row locks, backup before mutation, transactional updates, per-row BCrypt verification and unchanged non-password fields.

## Delivery
- Base commit 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c. Target local yudao-mysql / ruoyi-vue-pro, tenant 1.
- Reset 91 system_users rows (73 active records, 18 soft-deleted records) and 136 zsjos_partner_account rows to the user-supplied password. Each row uses independently salted BCrypt 2a, cost 4 matching the framework default. Plaintext password is not persisted in this record or backup.
- Selected target rows FOR UPDATE, verified and fsynced recovery backup before changes, updated both tables in one transaction, checked every hash before commit and again after commit. All 227 hashes matched. Compared every other column in memory, preserving all fields except password and update_time.
- Hash-only recovery backup with row IDs and original update times retained at backups/mysql/local-replace-20261109/passwords-before-reset-20261109.json.gz. Full decompression/content comparison passed. Existing full database recovery backup remains available.
- Tenant 1 scope follows repository instructions. Other tenant IDs found in imported system_users were outside this scope. Account enabled/deleted status was preserved. Login/browser verification was not performed.
- Ownership released. No migrations, application-source changes, commits or publication.
