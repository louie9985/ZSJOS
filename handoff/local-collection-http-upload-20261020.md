# 收集表 H5 局域网 HTTP 附件上传兼容修复

- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Workstream: local-collection-http-upload-20261020; owner Codex /root.
- Goal: support selecting, uploading and displaying collection attachments on LAN HTTP origins by reusing the existing compatible request-key generator.
- Non-goals: backend/API/storage/permission/database changes, archive changes, dependencies, shared service operations, Git publication.
- Branch: main; worktree: D:/ZSJ-OS; base commit: e94bc834e1d4d8d63c53ab5d3ceed7d8fa9a2671; target branch/integration order: None.
- Ownership: frontend/h5/src/pages/studentInfo/CollectionImages.vue; only the attachment compatibility sentence in docs/api/student-information-collection.md; frontend/h5/tests/collection-http-upload-browser.py; task-owned build/type/browser outputs under frontend/h5/tmp/collection-http-upload/; this record. Other concurrent workstreams and the existing dirty tsconfig.tsbuildinfo/main handoff remain preserved.
- Dependencies: existing utils/idempotency.ts, Vue/Vant/Axios and installed TypeScript/Vite/Playwright. Server-owned fields, attachment IDs and token contracts remain authoritative.
- Verification: full H5 vue-tsc/build with isolated incremental metadata/output; real production-page Chromium checks on insecure HTTP IP and HTTPS origins, desktop/mobile widths, image/document uploads and display/download, retry/delete, validation and submission payload. Synthetic test-only API interception; no business data or real account writes.

## Delivery — 2026-10-20 16:27 Beijing time

- Branch/worktree/HEAD: unchanged from registration (main, D:/ZSJ-OS, e94bc834e1d4d8d63c53ab5d3ceed7d8fa9a2671).
- User goal: allow local LAN HTTP collection attachment selection, upload and display.
- Decision/result: CollectionImages now imports and calls existing createIdempotencyKey instead of direct crypto.randomUUID. Insecure origins use the existing getRandomValues fallback; HTTPS retains native randomUUID; browsers without crypto use the existing final fallback. No API, field policy, encryption, permission or archive-binding changes.
- Changed files: frontend/h5/src/pages/studentInfo/CollectionImages.vue; scoped attachment sentence in docs/api/student-information-collection.md; frontend/h5/tests/collection-http-upload-browser.py; this record. Task build/type/browser artifacts are confined to frontend/h5/tmp/collection-http-upload/. Existing dirty shared tsconfig.tsbuildinfo and unrelated changes were preserved.
- Verification PASS: full H5 types with node node_modules/vue-tsc/bin/vue-tsc.js -p tsconfig.json --incremental --tsBuildInfoFile tmp/collection-http-upload/tsconfig.tsbuildinfo; full production bundle with node node_modules/vite/bin/vite.js build --outDir tmp/collection-http-upload/dist. The first attempted build-mode typecheck rejected the CLI override; switched to the same project in incremental project mode without changing source configuration.
- Verification PASS: python -B frontend/h5/tests/collection-http-upload-browser.py; actual production-page Chromium on HTTP IP at 1440/390px, HTTPS at 390px, and HTTP with crypto absent at 390px. Covered multipart field/side/content, in-flight submission block, decodable image thumbnail/preview, PDF download bytes, deletion, failed-upload retry, format/size rejection, exact submitted attachment IDs, no page errors and no horizontal overflow. Initial fixture close used mouse click for a touch-only Vant preview; corrected the test to a real touch tap and all scenarios passed.
- Verification PASS: git diff --check; mobile screenshot inspected. Evidence: frontend/h5/tmp/collection-http-upload/{dist,tsconfig.tsbuildinfo,http-1440.png,http-390.png,https-390.png,http-no-crypto-390.png}.
- Dependency/integration impact: None; existing H5 dependencies only. No shared runtime stop/restart, database writes, permissions, branch changes, commits or publication.
- Remaining: synthetic isolated APIs validate browser transport and presentation; real business storage and archive acceptance are unverified in the running environment. Existing local Vite may serve the updated source after refresh; packaged deployments require rebuilding/rolling out the H5 bundle. Ownership released on delivery.

## Scope update — 2026-10-20 preview follow-up

- Reopen the same local/main workstream for the user's broken image-preview report and instruction to continue. HEAD unchanged. Owner Codex /root.
- Goal: repair collection image preview dimensions, image fit and close interaction when the collection route is opened directly, including local Vite and production bundles.
- Ownership: reuse CollectionImages.vue, existing browser regression script, collection attachment documentation paragraph, this record and task-owned output directory. No shared/global stylesheet edits are planned; collection-only preview styling is owned by the collection component.
- Verification addition: fresh browser contexts, HTTP IP origin with assets from existing local Vite (all business APIs intercepted), production HTTP/HTTPS at desktop/mobile sizes, non-square decodable image, actual preview/image bounding boxes and aspect ratio, transparent preview surface, close button/touch dismissal, screenshots inspected. Preserve prior HTTP upload fix and unrelated dirty files.

## Delivery — 2026-10-20 18:18 Beijing time

- Branch/worktree/HEAD: unchanged from registration (main, D:/ZSJ-OS, e94bc834e1d4d8d63c53ab5d3ceed7d8fa9a2671); environment local.
- User goal: fix the collection attachment image preview appearing as a narrow white stripe.
- Diagnosis: fresh direct navigation using current Vite assets lacked the image-preview stylesheet. Measured preview width 28.31px at a 1440px viewport, with the generic popup glass background/blur applied. Prior test checked preview visibility but did not validate geometry; its 1px fixture could not establish correct scaling.
- Result/key decisions: explicitly import the existing Vant image-preview stylesheet in CollectionImages.vue; pass a collection-only class and closeable option; reset border/background/shadow/backdrop only for that teleported viewer. Shared main/global styles and unrelated preview consumers were not edited. Prior HTTP upload compatibility remains intact.
- Changed files this turn: CollectionImages.vue; attachment preview sentence in docs/api/student-information-collection.md; existing collection-http-upload-browser.py regression; this record. Task output remains under frontend/h5/tmp/collection-http-upload/; preserve unrelated dirty files and shared tsconfig.tsbuildinfo.
- Verification PASS: full H5 vue-tsc with isolated incremental output and Vite production build. Python regression passed both --dev (current existing port 10086 assets) and production bundle modes, each on HTTP 1440/390/320px, HTTPS 390px and HTTP without crypto 390px (10 scenarios total). All business APIs intercepted; no business service requests/writes. Local Vite module readback confirms imported style, collection preview class and close button.
- Verification PASS: fresh contexts, visible 400x240 PNG, full viewport preview bounds, aspect-preserving image fit, settled opacity, dark overlay, transparent/unblurred surface, mouse close button and touch dismissal; existing multipart upload/progress/retry/delete/PDF byte download/validation/submission checks retained. Screenshots after transitions visually inspected: dev-http-preview-390.png and bundle-http-preview-1440.png. Scoped diff check passed; final test-only output filenames were separated by dev/bundle/crypto mode and Python syntax checked.
- Dependency/integration impact: None. No dependencies, server restarts, permissions, database writes, Git branch operations, commits or publication. Collection-specific CSS is kept separate from generic popup theme.
- Remaining: refresh the local collection page to load current assets. Actual user attachment/runtime data was not accessed; packaged target deployment and real business storage/archive acceptance remain outside this frontend visual verification. Ownership released on delivery.
