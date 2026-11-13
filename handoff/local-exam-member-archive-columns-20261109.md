# Exam collection member archive columns

- Environment local, main at 7a7cf5e14b, worktree D:/ZSJ-OS. Owner /root/backend_acceptance, coordinator /root.
- Goal expose separate student name/mobile and archive name/mobile/identity in exam member roster. Frontend owned by frontend_acceptance.
- Ownership ExamCollectionQueryService.java, ExamCollectionVO.java, focused query tests and this record. Reuse existing batch StudentSummaryService.projectForExam, leaving concurrent archive/task/studentcontact changes owned by others.
- Student name comes from current ServiceRelation.studentNameSnapshot, mobile from its current Person; archive columns come only from latest formal service archive or submitted registration archive. Missing archive stays empty. Planner API unchanged.
- Verification isolated current-source Java suite, focused batch projection/no-fallback tests, diff checks. No database mutation, deployment, staging or commit. Integration target None.

## Delivery
- Exam Member response keeps studentName/mobile and appends nullable archiveName/archiveMobile/archiveIdCard. Query calls existing StudentSummaryService.projectForExam once for the page's current services, avoiding per-member archive reads.
- studentName retains current service.studentNameSnapshot; mobile uses summary student.mobile from the service's current Person. Archive values use only info.name/info.mobile/info.id_card. Shared projection reads latest service revision, otherwise corresponding SUBMITTED registration form with latest formal revision; no draft/master contact/identity-hash substitution.
- Projection is independent of lastSubmissionId, so members missing this task's submission can still display an existing formal archive. No planner DTO/API change.
- Added focused tests for different student/archive names and mobiles, real identity value versus frozen hash, null archive fields, one batch call for multiple members, no per-row archive-store calls, and empty-page zero archive reads. Existing StudentSummaryService tests in the same suite cover submitted/draft and latest service-revision precedence.
- Current-source isolated compile and 121/121 Java tests passed, 26 containers with zero failures/skips. Scoped diff check passed. Frontend acceptance remains coordinated by frontend_acceptance; no service restarted or database written, so deployed HTTP response requires subsequent runtime publication.
- Backend ownership released, no commit or staging performed.
