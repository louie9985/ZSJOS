# navigation-visibility-20261028
Environment: local (environment variable/file absent). Owner: Codex. Branch: main.
Worktree: D:/ZSJ-OS. Base: 0628f72f2c6208a6cc553f3f29b893de8f4ec697.
Goal: personal navigation visibility on both clients. Non-goals: authorization, database schema, deployment.
Ownership: navigationOrder utilities/tests, NavigationOrderModal/NavigationOrderDialog, NavigationPreferenceVO/Service and tests, personal-navigation documentation and browser checks, this record.
Dependencies: existing preference JSON storage and query/update permissions. Integration: None.
Verification: dual-client utility tests, actual component browser checks, focused backend tests where available.

Implementation: optional sibling hiddenKeys stored in existing JSON, validated with existing input limits; both clients filter display trees while editors retain hidden rows. Sorting preserves visibility. Existing authorization untouched.
Browser harness output moved to tmp/navigation-visibility-verification to avoid Maven target cleanup.
Verification: 12 dual-client utility tests pass; actual Vue/React fixtures pass save/reload, hide/reopen/restore, cancel, failure retry, reset, denied and 1280/375/320 widths. Mobile screenshots inspected. These are fixture requests, not live-account acceptance.
Backend verification: Maven reactor focused NavigationPreferenceServiceTest passed (7 tests, 0 failures/errors). No deployment/restart or live-account verification performed; full frontend typechecks/production builds not run, actual component fixture builds passed. Delivery complete; ownership released.
