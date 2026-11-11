# Exam collection todo isolation

- Environment local (environment variable and /etc override absent). Workstream local-exam-todo-isolation-20261106, owner /root.
- Branch main, worktree D:/ZSJ-OS, base 7a7cf5e14b. Integration target/order None.
- Goal isolate per-planner updates and cancel obsolete generations before regeneration.
- Non-goals deployment, direct database repair, frontend changes, unrelated concurrent edits.
- Ownership service/examcollection/ExamCollectionTodoService.java, service/task/BusinessTaskCommandService.java, dal/mysql/task/BusinessTaskMapper.java, new ExamCollectionTodoServiceTest.java and BusinessTaskPendingUpdateTest.java, docs/api/exam-material-collection.md (append only), this record. Backend paths relative to backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos; tests under corresponding src/test/java.
- Dependencies existing task command API and roster transaction. Verification focused current-source Java compilation/JUnit and scoped diff checks.

## Delivery

- Exam collection refresh uses the ID returned by idempotent creation and restores that generation owner. Other callers retain the existing reassignment behavior. Completion targets the original generation key, avoiding cross-planner completion when legacy owners are corrupted.
- Generation replacement/closure cancels all task pending records before planner iteration, including anomalous owners and empty rosters.
- Current changed Java sources compiled against the prepared local classpath with explicit Lombok processing in artifacts/exam-todo-isolation. Seven focused JUnit tests passed, including real H2/MyBatis SQL execution, tenant isolation, completed-row protection, per-planner updates, original-key completion and cancellation order. Scoped git diff --check passed.
- Production database and live HTTP/browser acceptance unverified. Backend runtime was not restarted or deployed. Existing anomalous data is repaired only on the relevant synchronization/replacement path; this delivery does not execute a direct database repair.
- Preserved concurrent work and appended API documentation only. Ownership released.
