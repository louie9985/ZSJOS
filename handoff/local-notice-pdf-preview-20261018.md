# 公告 PDF 内置预览修复

## Registration — 2026-10-18 16:24 北京时间

- Environment: local (ZSJOS_AGENT_ENV unset, /etc/zsjos/agent-environment absent). Workstream: notice-pdf-preview-20261018; owner: Codex /root.
- Branch: main; worktree: D:/ZSJ-OS; base/HEAD: 6bf0ce81a182b0a5ea040c8536a1ae7fad485e5e. Target branch/integration order: None.
- Goal: restore announcement PDF preview in Edge through the existing Workbench attachment component.
- Non-goals: Admin/H5, backend authorization/storage, database, dependencies, deployment/restart, Git publication.
- Ownership: frontend/workbench/src/components/AttachmentCard.tsx, PdfAttachmentPreview.tsx; src/services/pdfAttachmentPreview.ts and .test.ts; test/notice-pdf-preview.html, .tsx, -browser.py; docs/frontend/notice-center.md (prior image-preview ownership released; preserve existing changes); this record.
- Dependencies: existing signed file URLs and authorized blob loaders; React/Ant Design; installed Edge/Playwright and existing local Vite service.
- Decisions: fetch PDF bytes without adding credentials; validate response status, MIME and PDF signature; embed only a normalized application/pdf blob without iframe sandbox; own cancellation, retry and object-URL cleanup inside PDF preview. Original download stays on the authorized loader. Cross-origin storage must permit readable CORS responses; failure remains visible and downloadable.
- Verification plan: focused service and existing attachment tests; Workbench typecheck; Edge desktop/mobile fixture using actual NoticeAttachments and generated valid PDF, error/retry/non-PDF rejection/download/cleanup; scoped diff. Preserve all unrelated current edits.

## Delivery — 2026-10-18 16:29 北京时间

- Status: delivered locally; ownership released. Environment/branch/worktree unchanged; HEAD remains 6bf0ce81a182b0a5ea040c8536a1ae7fad485e5e. Registration time above was rounded in advance; registration preceded implementation.
- Result: AttachmentCard delegates PDFs to PdfAttachmentPreview. The service validates HTTP status, response MIME and PDF header before embedding a normalized application/pdf blob without the Chromium-blocking sandbox. Remote HTML is never embedded; credentials are omitted. Loading, differentiated errors, retry, cancellation and object-URL cleanup are implemented. Existing original download and media flows remain unchanged.
- Changed files: the 8 implementation/test/document files and this record listed in Registration. Existing notice-center image-preview documentation preserved. No backend, database, new dependency, branch, commit, push, deployment or shared-service mutation.
- Verification: 33 focused Vitest cases passed (17 PDF content boundary, 5 notice resource, 11 attachment types); final npm run typecheck passed; scoped git diff --check passed. Edge 155.0.4283.45 real-browser fixture passed at 1440 and 390 widths: native application/pdf embed present, no chrome-error frame, original file download, HTTP 403/retry, HTML rejection, close and unmount URL cleanup. Authorized blob loader compatibility also passed through real Node fetch. Browser fixture corrections handled Ant Design Chinese-button spacing and closing-animation selectors; initial header regex escaping was corrected before final passing checks.
- Evidence: frontend/workbench/test/notice-pdf-preview-browser.py; screenshots captured at C:/Users/EDY/AppData/Local/Temp/notice-pdf-preview-20261018/edge-1440.png and edge-390.png. Native viewer DOM/URL checks passed; screenshot display through the tool did not provide visual inspection evidence.
- Limits: synthetic PDF/file responses, not authenticated acceptance of the screenshot's actual attachment. Cross-origin storage must allow CORS reads; its live headers/config were not verified or changed. Physical mobile browsers, disabled-PDF browser policies and target deployment remain unverified. No production build required for this component-only behavior change (no dependency, route, asset or build-config change).
- Remaining work: deploy updated Workbench assets under separate authorization and verify the actual announcement attachment, including file-service CORS and any site CSP. Integration impact: all Workbench AttachmentCard PDF consumers use the same validated preview path; Admin/H5 and backend permission contracts unchanged.
