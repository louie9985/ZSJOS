# 考试名单学员与档案字段前端

- Environment local, ZSJOS_AGENT_ENV unset. Owner /root/frontend_acceptance, coordinated by /root.
- Worktree D:/ZSJ-OS, branch main; current Git base inspected before edits. Existing unrelated changes preserved.
- Goal: five distinct student/archive columns, preserving class/planner/status/actions and empty archive values.
- Ownership: frontend/workbench/src/services/examCollection.ts; frontend/workbench/src/components/examCollection/ExamTaskPanels.tsx; temporary browser fixture/evidence; this record.
- Dependencies: backend_acceptance provides archiveName/archiveMobile/archiveIdCard, retaining studentName/mobile.
- Verification: Workbench typecheck, isolated real-component browser fixture with differing and absent archive values, desktop/mobile horizontal scroll.
- Integration None; contributor does not commit or deploy.

## Delivery
- Base HEAD 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c. Preserved pre-existing ExamTaskPanels BusinessTable props modification, changed only member column definitions and ExamMember optional archive fields.
- Five columns use studentName/mobile/archiveName/archiveMobile/archiveIdCard respectively. Existing class/planner/status/action columns retained; missing archive values render the shared table '-' placeholder. Planner interface untouched.
- Workbench npm run typecheck passed; scoped git diff --check passed. Isolated real-component browser fixture used intentionally different student/archive names and phones plus absent archive values. Verified DOM first five values match their own sources, absent archive remains three '-' values. Desktop1440 and mobile390 have no whole-page overflow; shared table scroll width1490 (mobile viewport358) supports horizontal scrolling. Screenshots under artifacts/exam-member-columns inspected; browser space58 closed and owned Vite5192/session53890 stopped.
- Backend agent reports121/121Java tests with new contract. This frontend browser verification uses synthetic API data; running backend was not restarted. No commit/database/deployment action.
