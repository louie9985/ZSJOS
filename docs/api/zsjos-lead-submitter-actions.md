# Lead submitter actions and complaints

## Submission channels

- The independent partner frontend uses `/part-api/zsjos/**`. `POST /part-api/zsjos/lead/create` requires `zsjos:lead:submit` and accepts an enabled ordinary partner; admin/workbench calls remain under `/admin-api/zsjos/**`.
- Partners may use automatic dispatch only. New-media employees may specify only configured assignment candidates. New-media managers may specify any enabled eligible sales user.
- `POST /admin-api/zsjos/lead/self-sourced/create` requires `zsjos:lead:self-sourced:create`. The server forces direct self-ownership and never places the Lead in automatic dispatch or the claim pool.
- Duplicate review snapshots the original source type and partner subject. A weak-match review therefore preserves self-sourced direct ownership and historical submitter rights when it later creates the Lead.

## Submitter actions

- `PUT /part-api/zsjos/lead/{id}/submitter-supplement` updates region, category, intended products, and remark only. It cannot change name, mobile, or WeChat.
- `POST /part-api/zsjos/lead/{id}/urge` records at most one urge per Lead, submitter, and Beijing date and notifies the current owner.
- `POST /part-api/zsjos/lead-complaint/lead/{leadId}` creates an independent complaint with optional validated image evidence; `GET /part-api/zsjos/lead-complaint/my-page` returns the current partner's complaint history.
- Historical rights use the immutable `sourceUserId`. The system account must remain enabled; partner submissions additionally require the same partner subject to remain enabled.
- Invalid, closed, and won Leads reject supplement, urge, and complaint commands.

## Complaint queue

- `GET /admin-api/zsjos/lead-complaint/page` and `POST /admin-api/zsjos/lead-complaint/{id}/decision` require `zsjos:lead-complaint:handle`.
- The queue is shared. Decision submission locks the tenant-scoped complaint row, so only the first pending-to-handled transition succeeds.
- Results are `founded` or `unfounded`; handler opinion is required and evidence is optional.
- Both `founded` and `unfounded` decisions notify the complaint record's actual complainant. An employee complainant is resolved from `complainantUserId`; a partner complainant is resolved from `partnerId` to that partner's account. The recipient is never inferred from the current Lead submitter, owner, role name, or display label.
- A founded complaint additionally retains the notification to the snapshotted sales user and that user's current direct department leader. An unfounded complaint has no additional sales-side recipient by default.
- Complaint result messages use `lead.no` as the customer-facing identifier. Clicking a result notification opens `/zsjos/leads/manage?leadId={internalLeadId}&tab=complaints`; the Lead object check and server-projected `visibleTabs` still apply, and an unavailable complaint tab falls back to the overview. A complaint decision does not change assignment, dispatch eligibility, Lead state, performance, or order data.
