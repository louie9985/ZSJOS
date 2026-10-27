# Production points issuance — 2026-10-26

## Registration — 11:37 +08:00

- Workstream: production-points-issuance-20261026; owner Codex /root.
- Environment: production by the user's explicit correction in this session. `/etc/zsjos/agent-environment` still reports test; this session follows the higher-priority user instruction. The marker is not changed.
- Branch/worktree/base: main; /opt/zsjos; 8279212fb97129ba6b006dbb0452ad843dfde4b5. Integration/target branch: None.
- Goal and authorization: create and enable only `pointsIssuanceJob` via the existing Infra API, allowing the two approved tenant-1 requests to issue real codes through the platform; verify persisted outcomes.
- Non-goals: application restart/deployment, global job synchronization, other job changes, business/permission SQL, external code disclosure, environment marker changes, commits/pushes.
- Ownership: this record and private operational executor/evidence under `/opt/zsjos-runtime/backups/points-issuance-20261026/`. Other workstreams and existing dirty files remain untouched.
- Dependencies: running backend/Quartz, existing configured platform credentials, existing administrator authentication with `infra:job:create` and `infra:job:query`. Production operation is authorized; API authentication is currently unavailable.
- Verification plan: preflight configurations and approved queue; authenticated permission check; create once with handler pointsIssuanceJob, cron `0/10 * * * * ?`, retryCount 0, retryInterval 1000, monitorTimeout 120000; API and database readback; Quartz trigger, job logs, application status/code-presence booleans, UTF-8 name HEX; verify existing job definitions unchanged. Do not expose tokens or code contents.
- Diagnosis: two active tenant-1 requests are approved/queued with attempts=0; enabled=true and stored API key is present; no points task or Quartz triggers. Current application logs confirm non-clustered quartzScheduler started. The stale schedulerName cluster heartbeat does not describe this running scheduler.
- Recovery boundary: if future suspension is requested, pause this task through its API; already-generated external codes cannot be rolled back by deleting the task or changing local status. No recovery action authorized or executed.

## Checkpoint — 2026-10-26

- Prepared private `execute.py`: explicit operator token-file input, server-side permission preflight, exact queue/configuration guards, one create POST with readback on uncertain outcome, verification polling and unrelated-job digest comparison. Tokens and redemption code contents are not saved or printed.
- Verification: Python syntax check and SELECT-only production preflight passed; scoped Git diff check passed. The same two approved/queued requests still have zero attempts, and the points job/Quartz trigger/logs are absent. Configured feature switch and stored-key presence confirmed; credential validity still awaits a real issuance call.
- Pending: operator-authorized administrator token file with Infra create/query permissions. Asked for the file path, not a token in chat. No administrator credential/session was available in the configured environment or runtime secret-file names. No session was harvested from the database.
- No task created, no business/database/configuration write, no platform call, no service restart. Production operation remains authorized; only authentication is missing. Source implementations and unrelated work preserved. Registration retained for continuation.

## Registration update — 2026-10-26

- User supplied corrected credentials and explicitly authorized login. Use only the latest supplied credential, without persisting it. Executor accepts an in-memory token; authenticate through the normal login API, verify configured permissions and log out the newly created session afterward. All other scope/guards remain unchanged.

## Delivery — 2026-10-26 11:44 +08:00

- Normal login and permission preflight succeeded using the latest user-provided credential; credential/token remained in memory. Created exactly job 7 through `/admin-api/infra/job/create`, then authenticated GET readback confirmed it. Own login session was logged out.
- Job: pointsIssuanceJob, enabled/status=1, cron `0/10 * * * * ?`, empty parameters, task retries 0, retry interval 1000 ms, monitor timeout 120000 ms. Existing four job definitions unchanged by before/after digest; no global synchronization.
- Both original tenant-1 applications are now approved/issued, attempts=1, code_status=available, last_error empty, encrypted-code and external-ID presence verified without reading code contents. notified=1 confirms durable notification acceptance.
- Quartz trigger registered under the running non-clustered quartzScheduler; repeated successful execution logs observed. Original backend PID/start time unchanged (2247320, 08:48:15); no restart/deployment or source implementation change.
- Verified task name through utf8mb4 and HEX equality; private before/create/readback/final evidence in `/opt/zsjos-runtime/backups/points-issuance-20261026/`. Python syntax and Git diff checks passed. No real-browser code reveal was performed; the applicant can refresh and reveal through the normal authorized page.
- Files: this registration/delivery record and private executor/evidence only. Existing unrelated source changes preserved. No permission changes, commits or pushes. Task complete; write ownership released.
