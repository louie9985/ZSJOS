# Lead submitter actions and complaints

## Submission channels

- `POST /admin-api/zsjos/lead/create` requires `zsjos:lead:submit` and accepts only a server-resolved new-media employee, new-media department manager, or enabled ordinary partner.
- Partners may use automatic dispatch only. New-media employees may specify only configured assignment candidates. New-media managers may specify any enabled eligible sales user.
- `POST /admin-api/zsjos/lead/self-sourced/create` requires `zsjos:lead:self-sourced:create`. The server forces direct self-ownership and never places the Lead in automatic dispatch or the claim pool.
- Duplicate review snapshots the original source type and partner subject. A weak-match review therefore preserves self-sourced direct ownership and historical submitter rights when it later creates the Lead.

## Submitter actions

- `PUT /admin-api/zsjos/lead/{id}/submitter-supplement` updates region, category, intended products, and remark only. It cannot change name, mobile, or WeChat.
- `POST /admin-api/zsjos/lead/{id}/urge` records at most one urge per Lead, submitter, and Beijing date and notifies the current owner.
- `POST /admin-api/zsjos/lead-complaint/lead/{leadId}` creates an independent complaint with optional validated image evidence.
- Historical rights use the immutable `sourceUserId`. The system account must remain enabled; partner submissions additionally require the same partner subject to remain enabled.
- Invalid, closed, and won Leads reject supplement, urge, and complaint commands.

## Complaint queue

- `GET /admin-api/zsjos/lead-complaint/page` and `POST /admin-api/zsjos/lead-complaint/{id}/decision` require `zsjos:lead-complaint:handle`.
- The queue is shared. Decision submission locks the tenant-scoped complaint row, so only the first pending-to-handled transition succeeds.
- Results are `founded` or `unfounded`; handler opinion is required and evidence is optional.
- A founded complaint notifies the snapshotted sales user and that user's current direct department leader. It does not change assignment, dispatch eligibility, Lead state, performance, or order data.
