# 考试收集已通知班级调整前端

- Environment local, owner /root/frontend_acceptance under /root. Branch main D:/ZSJ-OS, base7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c. Existing unrelated/main edits preserved.
- Scope: ExamCollectionPage.tsx, ExamCollectionDialogs.tsx, services/examCollection.ts, examClassSelection.ts and focused test, temporary fixtures, this record.
- Root cause: task details showed notification only for empty classIds; first notification removed the only class selection entry.
- Goal: OPEN+members authorized adjustment, existing class selection retained, explicit additions/removals and reason, historical unavailable class visible/removable, unchanged class frozen roster stays unchanged.
- Backend agent owns option/command contract and lifecycle behavior. No real notifications, database operations, deployment or contributor commit.
- Scope includes ExamTaskPanels.tsx only for translating new ADJUST_CLASSES audit action.
- Verify: focused selection tests/typecheck, isolated browser adjustment payload and unchanged/default selection, desktop/mobile component behavior. Integration target None.

## Delivery
- OPEN tasks with existing classIds now expose members-authorized adjustment. Existing first-notification notify permission remains separate. Shared dialog defaults original IDs, displays historical unavailable class labels, reports additions/removals, requires a reason and disables unchanged/empty/incompletely loaded submissions. Retained-class-aware tree helper permits retaining/removing original unavailable IDs while blocking new unavailable IDs. New ADJUST_CLASSES event translated.
- Workbench typecheck, final build and3/3 selection helper tests passed. Browser actual component fixture starts [1,9] with historical9 selectable=false; untouched save disabled, original labels present. Group select then remove9 produces additions1/removals1, save disabled without reason; submitted payload revision5/classIds[1,2]/reason. Simulated conflict retains selection/reason; second attempt invokes saved callback. 1280/390 screenshot evidence inspected with select height32 and no whole-page overflow. Screenshot window minimized once, restored the unique Ego window under existing authorization before capture.
- Evidence artifacts/exam-adjust-classes/desktop.png and mobile.png; fixture uses synthetic APIs only. Space64 finished and owned Vite5192/session93616 stopped. Backend agent separately reports129/129 tests; backend service deployment/real notifications were not performed. All earlier unrelated modified files preserved; contributor did not commit.
