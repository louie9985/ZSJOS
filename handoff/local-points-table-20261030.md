## Registration — local-points-table-20261030
- Environment local (variable/file absent); owner Codex; main; D:/ZSJ-OS; base ba8c788f470eb96d22e8729a2d8f711d83c0c370; integration None.
- Scope: PointsPackagePage.tsx, pointsRedemptionApi.ts, PointsRedemptionController.java, PointsRequestService.java, PointsPackagePermissionProvider.java, docs/api/points-redemption.md, this record.
- Goal: full table controls and package CRUD. No database writes, grants, deployment or dependencies. Verification: TypeScript, scoped diff; backend/browser checks as available.

## Delivery
- Implemented complete-list local sorting, name search, boolean status filter and size-selectable pagination through BusinessTable; retained create/edit and added success feedback. Added permission-protected logical deletion with object check and transactional package row lock; documented manual menu/button configuration without granting permissions.
- TypeScript check and scoped diff check passed. Real browser, live API/database, and Admin compatibility acceptance not executed; no authenticated browser setup established for this run. No database writes, deployment or role changes. Backend compile result reported in chat. Ownership released.
