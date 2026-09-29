# Mobile modal scroll repair

## Registration — 2026-09-29
- ID: local-mobile-modal-scroll-20260929; owner: this chat /root.
- Environment: local (environment variable and environment file absent; fallback).
- Branch: main; worktree: D:/ZSJ-OS; base: 5fadfb3e9be563ac4cb3e5c142308549944aa465.
- Goal: restore touch scrolling and reachable footer actions in the mobile follow-up modal by fixing the shared Ant Design modal height chain.
- Non-goals: business/API/permission changes, dependencies, database writes, deployments, service changes, branch/worktree operations, commits or pushes. Preserve unrelated changes.
- Ownership: frontend/workbench/src/styles/antd-overrides.css; frontend/workbench/docs/follow-up-form.md (preserve earlier delivered edits); frontend/workbench/test/mobile-modal-scroll-browser.py; this record.
- Dependencies: existing FollowUpModal browser fixture, installed Ant Design runtime, existing local Vite listener and Playwright. No active conflicting owner found for the shared style; follow-up documentation's prior workstream is delivered.
- Verification: real-component touch swipes at 320/390/768px and shortened viewport, desktop layout and footer reachability, existing form-flow browser test, focused style guards, screenshots and scoped diff/UTF-8 checks.
- Target branch/integration order: None.

## Delivery — 2026-09-29
- Fixed both vh fallback and dvh mobile height rules to target the actual Ant Design 6 .ant-modal-container. Body now shrinks within the bounded column and accepts touch scrolling while header/footer remain visible. Desktop behavior is unchanged.
- Updated follow-up form documentation and added a browser regression using the existing real-component fixture; no new dependencies or production mock data.
- Verification PASS: mobile-modal-scroll-browser.py at 320x568, 390x844, 390x430, 768x430, and desktop 1280x1000. Real touch swipes scroll the body forward/back; observed forward offsets 382/207/244/245px; header/footer stay fixed and no horizontal overflow. Mobile and desktop screenshots visually inspected.
- Verification PASS: existing won-follow-up-browser.py covers modal/inline forms, optional/required future reminder validation, submission and success refresh using isolated transport fixtures.
- Style guards: 29 passed, 1 unrelated pre-existing failure in src/styles/components/content-review-attachments.css:6 (font-size: 28px). That clean tracked file is unchanged by this work; no unrelated correction made.
- Scoped diff/UTF-8 checks passed. Only the two responsive selectors and explanatory comment changed in production CSS; earlier follow-up documentation edits are preserved.
- Not verified: physical phone/Safari and real soft keyboard; shortened viewport checks are not keyboard acceptance. No live authenticated API or deployment performed. Type/production build checks are not applicable to this CSS-only behavior correction without build input/dependency changes.
- Ownership released for the four registered files. No branch/worktree/service/database/account changes, commit, push or deployment.
