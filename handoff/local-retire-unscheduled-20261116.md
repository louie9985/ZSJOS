# Stop creating unscheduled classes

- Environment local (environment variable unset, default local); owner current Codex chat.
- Branch main; worktree D:/ZSJ-OS; base 21631a8a98a62c41a3c71596a6739e03a535c3b6; integration None.
- Goal: stop automatic creation of UNSCHEDULED; logically delete empty UNSCHEDULED classes in tenant 1 development ruoyi-vue-pro after runtime update.
- Scope: UnscheduledDeliveryClassService.java, its focused test, docs/api/delivery-class-management.md, this record and scoped cleanup SQL if needed. Preserve existing concurrent edits.
- Non-goals: PENDING/NORMAL/EXCEPTION classes, occupied classes, business data deletion, permissions.
- Dependencies: runtime restart requires explicit operation approval under root section 4; old runtime recreates empty classes every minute.
- Verification: focused tests + compile, scoped DB audit/backup/repeatable cleanup, browser after runtime activation. Empty includes any undeleted service regardless of status; protect pending transfer targets and historical references.

## Implementation and pending activation

- Removed missing-class insertion from ensure; absent UNSCHEDULED returns null, existing historical class synchronization retained. Legacy callers already reject null through REGISTRATION_ROUTE_INVALID.
- Updated focused tests for repeated missing lookups/no insertion and existing reuse; repaired test metadata initialization and query-all mock. All 5 tests PASS; module compile passed; scoped diff check passed.
- Read-only development audit: 18 empty candidates (73,113–129); five occupied classes retained. No DB rows changed.
- Server packaging blocked by inability to rename running target/yudao-server.jar to .original. Runtime PID48052 listens on48080. Need separately authorized stop/rebuild/start before cleanup, otherwise old scheduler recreates deleted classes. No service operation performed, no commits.

## Authorized activation and delivery — 2026-11-16

- User explicitly authorized stop/package/restart and scoped empty-class cleanup. Stopped PID48052, server packaging passed. Launched PID47908; healthy initially, subsequently exited without a confirmed cause. At continuation current PID15040 (started 16:19:17, same jar/local profile) was already running; did not start a competing process. Health HTTP200.
- Added repair/retire_empty_unscheduled_20261116.sql under the registered cleanup scope. Actual prerequisite-state transaction rehearsal affected18 then ROLLBACK; backup rows0/classes23 verified. Formal apply affected18; immediate replay0. No permission assignments or migration ledgers changed.
- Full row backup: ruoyi-vue-pro.zsjos_bak_empty_unscheduled_20261116 (18 rows). Only tenant1 audited empty UNSCHEDULED classes73,113–129 soft-deleted. Backup DDL remains; original rows and historical references retained. Recovery requires checking no replacement class before restoring deleted/updater/update_time/version from backup.
- At16:47 (over28min after current runtime start, multiple60s schedules), backup18, deleted18, remainingUNSCHEDULED5, emptyUNSCHEDULED0. Remaining five classes have64 undeleted services; zero services reference newly deleted classes. Representative class_name HEX validated as UTF-8.
- Real browser using department-two supervisor at http://[::1]:5174: retry recovered class list; UNSCHEDULED group displays5 classes/64 services; searching deleted US528 yields0 classes; reset restores list. PENDING/NORMAL/EXCEPTION and occupied classes unchanged by cleanup.
- Focused tests5/5 and packaging passed. No commits/push. Ownership released. Scope does not add automatic deletion when an occupied class later empties.
