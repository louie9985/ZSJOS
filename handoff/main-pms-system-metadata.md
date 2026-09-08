# Workstream: main-pms-system-metadata

- Workstream ID: `main-pms-system-metadata`
- Goal: Extract PMS-owned System menus and dictionaries from the supplied `ruoyi-vue-pro.sql` snapshot and add them to the repository migration/bootstrap chain.
- Non-goals: No PMS schema or business-instance data, job configuration, role/package grants, application code, database execution, dependency changes, branch operations, commits, pushes, or unrelated worktree cleanup.
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- Base commit: `21a4f9b71555426f09d78dac7d891fce4aea4866`, preserving all existing uncommitted changes.
- Target branch: current local `main`
- Ownership scope: `script/sql/mysql/migrations/V189__pms_menu_and_dictionary.sql`; `script/sql/mysql/bootstrap.sql`; this handoff record.
- Owner: Codex `/root`
- Dependencies: Existing System menu and dictionary tables; Core migration V188; supplied UTF-8 SQL snapshot `C:\Users\EDY\Desktop\ruoyi-vue-pro.sql`.
- Integration order: extract and validate source rows -> add repeatable V189 metadata migration -> add V189 to bootstrap order -> run static SQL/content/encoding checks -> append delivery entry.
- Verification plan: verify exact source/repository row counts and stable IDs; verify menu parent closure and dictionary type/data references; run SQL static checks and `git diff --check`; execute against a controlled MySQL database only when a database environment and separate authorization are available.
