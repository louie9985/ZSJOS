# Lead submitter actions and complaints

## Submission channels

- The independent partner frontend uses `/part-api/zsjos/**`. `POST /part-api/zsjos/lead/create` requires `zsjos:lead:submit` and accepts an enabled ordinary partner; admin/workbench calls remain under `/admin-api/zsjos/**`.
- Partners may use automatic dispatch only. New-media employees may specify only configured assignment candidates. New-media managers may specify any enabled eligible sales user.
- `POST /admin-api/zsjos/lead/self-sourced/create` requires `zsjos:lead:self-sourced:create`. The server forces direct self-ownership and never places the Lead in automatic dispatch or the claim pool.
- Duplicate review snapshots the original source type and partner subject. A weak-match review therefore preserves self-sourced direct ownership and historical submitter rights when it later creates the Lead.

## Submitter actions

- `PUT /part-api/zsjos/lead/{id}/submitter-supplement` (and the existing ADMIN equivalent) updates region, category and intended products, and appends the optional remark. It cannot change name, mobile, WeChat or the existing Lead remark. A blank remark does not add a remark entry.
- `POST /part-api/zsjos/lead/{id}/urge` records at most one urge per Lead, submitter, and Beijing date and notifies the current owner.
- `POST /part-api/zsjos/lead-complaint/lead/{leadId}` creates an independent complaint with optional validated image evidence; `GET /part-api/zsjos/lead-complaint/my-page` returns the current partner's complaint history.
- Historical rights use the immutable `sourceUserId`. The system account must remain enabled; partner submissions additionally require the same partner subject to remain enabled.
- Invalid, closed, and won Leads reject supplement, urge, and complaint commands.

## Remark history contract

- Existing ADMIN and PARTNER detail responses include `remarkHistory` and `remarkHistoryIncomplete`; list responses do not load event history. Each entry contains `id`, `kind` (`submission`, `supplement`, `legacy`), `content`, and nullable `occurredAt`/`operatorName`. Times follow the existing epoch-millisecond JSON contract. History is available under existing detail authorization, independently of flow-history permission. Author names follow existing identity masking; no subject IDs or event JSON are exposed.
- Supplement events remain `lead_submitter_supplemented`. Their typed `relatedObjectRefs` snapshot retains `before` and adds `remarkMode=append_v1`, the trimmed remark (empty string when absent), typed submitter identity, name snapshot and SHA-256 normalized request digest. Writes and event insert are atomic. The existing tenant/idempotency unique index and a current read after the Lead lock protect replay. Same-key matching requests return success; mismatched or unverifiable legacy replay returns error `1900003130`.
- Clients retain the same key for retries of unchanged content, clear it after a successful operation/new form, and never prefill a new supplement with the old Lead remark. Each new submission is limited to 1000 characters; separate successful commands with identical text remain separate entries.
- Legacy events have only `before.remark`. Their exact nonblank texts and current Lead remark are shown once each as legacy entries, with no invented author/time. Internal projection evidence retains all source references. Missing/malformed evidence sets the incomplete flag while preserving readable content. No-event history cannot prove that an externally deleted event once existed; recovery is limited to surviving evidence.
- Initial/legacy entries precede new supplements; supplements sort by occurrence time and event ID ascending. New clients use the compatibility `remark` only when `remarkHistory` is absent, not when it is an empty array. Existing list/filter/notification `remark` consumers remain a single stored field, not an aggregate of new supplements.
- No schema migration or historical data rewrite is required. Deploy backend and clients together and refresh cached H5 forms. Reverting to the old backend reintroduces overwrite behavior; prefer a forward fix. Missing historical text needs independent evidence before any separate repair.

## Complaint queue behavior

- `GET /admin-api/zsjos/lead-complaint/page` and `POST /admin-api/zsjos/lead-complaint/{id}/decision` require `zsjos:lead-complaint:handle`.
- The queue is shared. Decision submission locks the tenant-scoped complaint row, so only the first pending-to-handled transition succeeds.
- Results are `founded` or `unfounded`; handler opinion is required and evidence is optional.
- Both `founded` and `unfounded` decisions notify the complaint record's actual complainant. An employee complainant is resolved from `complainantUserId`; a partner complainant is resolved from `partnerId` to that partner's account. The recipient is never inferred from the current Lead submitter, owner, role name, or display label.
- A founded complaint additionally retains the notification to the snapshotted sales user and that user's current direct department leader. An unfounded complaint has no additional sales-side recipient by default.
- Complaint result messages use `lead.no` as the customer-facing identifier. Clicking a result notification opens `/zsjos/leads/manage?leadId={internalLeadId}&tab=complaints`; the Lead object check and server-projected `visibleTabs` still apply, and an unavailable complaint tab falls back to the overview. A complaint decision does not change assignment, dispatch eligibility, Lead state, performance, or order data.
