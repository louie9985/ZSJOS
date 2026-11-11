# Local exam supervisor todos

- Environment local, ZSJOS_AGENT_ENV and /etc override absent. Owner /root, workstream local-exam-supervisor-todos-20261109.
- Branch main, worktree D:/ZSJ-OS, base 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c. Integration target/order None.
- Goal synchronize exam collection todos to configured delivery managers within existing class read scope and scope their inbox projection.
- Non-goals database writes, deployment, permissions assignment, unrelated existing changes.
- Ownership service/examcollection/ExamCollectionTodoService.java, ExamCollectionReminderService.java, new ExamCollectionRecipientScope.java and its test, existing ExamCollectionTodoServiceTest.java, docs/api/exam-material-collection.md (append), this record. Paths relative to zsjos Java/test roots.
- Dependencies existing System user/permission APIs, class object permission provider and business tasks. Verification current-source focused compile/JUnit and diff checks.

- Scope update includes ExamCollectionTodoTest.java fixture injection for new dependencies.

- Scope update includes ExamCollectionPlannerMembersTest.java and ExamCollectionReminderTest.java dependency fixtures.

## Delivery

- Implemented manager recipient resolution using enabled System accounts, configured managed-class/inbox capabilities and existing class read object authorization. No role-name inference.
- Planner and manager rosters merge per recipient. Existing lifecycle synchronization updates progress and completes visible collected rosters. Stale recipients are cancelled on synchronization. Inbox and paginated members apply the same current scope.
- Current changed Java sources compiled in artifacts/exam-supervisor-todos with existing local dependency classpath. Fourteen focused JUnit tests passed, covering planner isolation, manager visible/hidden classes, missing capabilities, combined recipient, completion, lifecycle cancellation and existing inbox/member behavior. Scoped diff checks passed.
- Live HTTP and Admin/Workbench browser acceptance remain unverified. Backend was not deployed or restarted. Existing tasks receive manager todos on their next notification/progress/roster synchronization. No database backfill executed.
- Existing concurrent edits preserved. Ownership released.
