# Registration — local-batch-exam-skill-20261009

- Owner: Codex /root; environment: local (ZSJOS_AGENT_ENV unset, environment file absent).
- Branch: main; worktree: D:/ZSJ-OS; base: c82c8605d86aef5c8f4cd328600bc7c3efc2bf90; target branch/integration: None.
- Goal: deliver a portable API-operated skill for batch entry through existing Zhongshijian single-record endpoints (user explicitly requested API instead of browser entry).
- Non-goals: application/API/database changes, real exam creation, notifications, service/dependency/Git operations.
- Ownership: this handoff file; C:/Users/EDY/.codex/skills/zsjos-batch-exam/**; C:/Users/EDY/.codex/skills/zsjos-batch-exam.zip. Unique record avoids concurrent writers to main.md.
- Dependencies: existing exam-calendar controllers, request/response objects, pagination and save/publish behavior; recipient needs HTTP capability and an authorized ADMIN token plus target tenant.
- Verification: skill validator, source-to-instruction review, relative-link and UTF-8 checks, ZIP integrity and byte-for-byte content validation. Live creation not authorized in this task.

## Delivery

- Delivered API-based zsjos-batch-exam skill, API reference, input examples, UI metadata and portable ZIP under C:/Users/EDY/.codex/skills/. Recipient needs HTTP capability, API base, tenant and securely configured ADMIN token; browser automation is not required.
- Reuses existing create and bodyless publish endpoints; includes complete pagination, permission preflight, per-row readback, partial-success accounting and unknown-write-outcome handling. No batch API or runtime code added.
- Verification: quick_validate.py PASS; UTF-8 and relative links PASS; ZIP integrity and all four archived files match source bytes. Contracts reviewed against controllers, request/response objects and current Workbench save/publish flow.
- Limits: no authenticated live HTTP creation/publish or recipient-agent execution performed; production end-to-end behavior remains unverified. No real exam records, database, services, permissions, dependencies or Git state changed. Existing unrelated edits preserved. Ownership released.

## Registration update — ordinary-user login

- Reacquired same file ownership and execution context. User corrected company name to 中世健 and established the sole tenant as ID 1; this supersedes the skill's generic tenant configuration requirement.
- Scope: correct all skill-facing names, fix tenant-id to 1, document normal account/password login through a secret input facility with agent-managed tokens. Recipient tool/OS requested to determine a concrete compatible login launcher; do not assume a secret UI exists.
- Verification: existing AuthLoginReqVO/RespVO, AdminAuthServiceImpl and Workbench login contract; static skill/link/archive validation. No real account login or server changes authorized/performed.
- User confirmed WorkBuddy on Windows. Scope adds a Windows PowerShell login dialog and narrowly allowed API client with DPAPI-protected local access token storage; no dependency or server change. Verify PowerShell syntax, mocked transport, credential persistence and failure boundaries offline; real UI/live credentials remain user-side validation.

## Delivery — WorkBuddy Windows login

- Corrected skill, references and metadata to 中世健; all helper requests use the explicitly confirmed sole tenant 1. Added Windows Forms secret login dialog and same-user Windows DPAPI encrypted access-token cache, plus a restricted HTTP helper for permission/list/create/bodyless publish. Password and refresh token are not persisted; no tokens emitted.
- Ordinary users enter normal credentials in a local dialog. WorkBuddy launches the script and handles API requests; implementer supplies the confirmed API base. Existing PC session limits and CAPTCHA remain enforced. No browser entry automation required.
- Verification: Windows PowerShell 5.1 offline helper check PASS: DPAPI round trip/no plaintext, URL validation, tenant header, UTF-8 JSON, bodyless publish, restricted routes, redacted transport errors and no write retry. PowerShell syntax and skill validator PASS; UTF-8/name/link checks and rebuilt six-file ZIP integrity PASS.
- Limits: actual WorkBuddy dialog and live account login/create not exercised; target API base still required. CAPTCHA-enabled deployment requires its existing human verification/session integration. Helper does not support attachment uploads; skill must pause attachment-bearing rows. No application, database, dependency or service changes. Ownership released.

## Registration update — supplied endpoint

- Same owner, scope and local execution context; user supplied testos.zhongshijian.top as the target system. Set default API base in helper and all directly affected skill instructions; no repository-wide environment constraint inferred from hostname.
- Read-only verification: HTTPS site returns 200; /admin-api/system/auth/get-permission-info returns application/json UTF-8 with code 401 and account-not-logged-in message, confirming the protected API entry. No credentials or mutations sent.

## Delivery — endpoint preset

- Default API base is https://testos.zhongshijian.top/admin-api in the Windows helper; examples omit the now-optional ApiBase argument. Skill and references no longer ask ordinary users to configure a URL or tenant.
- Verification: existing Windows PowerShell offline checks and skill validator PASS; encoding, links, placeholder removal and rebuilt ZIP integrity PASS. Live read-only API entry verified; actual credentials, WorkBuddy UI and record creation remain unverified. Ownership released.

## Registration update — observed WorkBuddy stall

- Same scope/context; user reports WorkBuddy stopped at missing cache and asked whether to launch login. Correct the conflicting stop-on-missing-credentials instruction, require attempting the provided login action before reporting a blocker, and add a double-click Windows login launcher as a fallback. Do not infer browser login history from absent helper cache. No live login or record creation.

## Delivery — first-use correction

- Removed instruction to stop merely because credentials are absent. Missing helper cache now leads to login launch; a missing exam list does not block independent login. Added login.cmd fallback with script-relative path and visible error retention.
- Skill validator, relative links, UTF-8 and seven-file ZIP byte/integrity checks PASS. Launcher reviewed without executing real login. WorkBuddy desktop execution remains unverified; API helper unchanged from prior offline-tested version. Ownership released.

## Registration update — name-based colors

- Same owner, local environment, worktree and skill-file scope. Apply user's explicit naming rule when preparing new exam requests: 考试时间 -> #6ABF6D; 截止时间 -> #FF8F8F. Update skill and directly related examples/API guidance, rebuild ZIP; no existing-record or application changes.
- Verification: skill validation, scoped content/relative links and ZIP integrity. The existing backgroundColor API is unchanged.

## Delivery — name-based colors

- Agent now computes explicit backgroundColor from exact name substrings before preview, deduplication and JSON creation, and checks that color on readback. Both keywords require clarification; unmatched names retain explicit/default color. Examples and API notes synchronized; existing records untouched.
- Skill validator and scoped color/UTF-8/link/ZIP checks PASS. Documentation-only instruction change; runtime helper unchanged and no real creation performed. Ownership released.

## Registration update — enforced retry protection and publish confirmation

- Reacquired existing scope and local context. User reports duplicate execution and asks for idempotency plus a publish confirmation button. Add helper-enforced preflight, complete list reads, durable per-record journal and same-user/base exclusive lock. Unknown create/publish outcomes fail closed; no claim of cross-machine server idempotency.
- Add explicit batch Publish action selecting IDs from a JSON file, showing current records and a confirm-publish button; legacy single publish must pass the same dialog. No deletion/revocation of existing duplicates and no live account writes.
- Verification: offline stateful API doubles covering replay, concurrent lock, partial/uncertain writes, conflicts, cancellation, publish rechecks and retry; PowerShell 5.1, skill and package checks. UI construction check; actual WorkBuddy/live business flow remains separate.

## Delivery — script-enforced replay protection and publish button

- Added exam-safety.ps1: server permission preflight, complete pagination, content-aware duplicate lookup, per-target exclusive local file lock and atomic intent journal before write. Existing identical draft/published record returns its original ID; conflicting/multiple records or unresolved prior intent block create. Journal uses hashed identity/content and ID, without tokens or record text. No automatic write retries or journal-reset escape hatch.
- Added Publish action accepting an explicit JSON ID array and Windows Forms “确认发布” / “保留草稿” buttons. Legacy Request publish delegates to the same guard. Confirmation lists current content; rechecks before publishing; skips already-published IDs and blocks duplicate drafts. Cancel writes nothing. Partial/unknown publication preserves completed rows and stops; no automatic destructive cleanup.
- Verification: packaged offline test-exam-safety.ps1 PASS in Windows PowerShell 5.1: repeated create, exclusive lock, missing permission, incomplete and multi-page reads, lost committed responses, unknown write replay blocking, content/duplicate conflicts, cancellation, confirmed batch, repeated published IDs, partial batch failure, content changed during confirmation, and legacy Request cannot bypass protection. Actual Forms controls constructed and checked without displaying a live confirmation. Entry syntax, skill validation, BOM/UTF-8, links and nine-file ZIP integrity PASS.
- Fixes during verification: shortened journal paths for .NET Framework Windows path limits; atomic File.Replace uses a named backup because PowerShell 5.1 null argument conversion differs. Live server was not mutated.
- Limits: no server idempotency/transaction/version boundary added; other machines/Windows users/clients can still race. Actual WorkBuddy visual interaction and authenticated live execution remain unverified. Existing duplicate records untouched. Ownership released.

## Registration update — start-time color

- Same scope, owner and local execution context. Add explicit name substring 开始时间 -> #00D0FF alongside existing colors, generalize multiple-keyword conflict handling, synchronize examples/API reference and rebuild ZIP. Documentation-only; no runtime or existing-record changes. Verify skill, scoped content, links and archive.

## Delivery — start-time color

- Added 开始时间 -> #00D0FF to skill, example and API guidance; multiple matched keywords require clarification. Skill validator and three-color/UTF-8/link/ZIP checks PASS. Script unchanged, no live write performed. Ownership released.
