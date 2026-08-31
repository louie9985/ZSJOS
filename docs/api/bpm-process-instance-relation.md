# BPM related approval contract

`ProcessInstanceSelect` is available only in deployed FormCreate launch forms. Its persisted value is a
`string[]` of target process-instance IDs. Required validation continues to use FormCreate's ordinary
field rules; the component itself is always multi-select and accepts at most 20 unique IDs.

## Launch and storage

The existing `POST /admin-api/bpm/process-instance/create` request shape is unchanged. The BPM service
recursively discovers `ProcessInstanceSelect` rules from the deployed `formFields` snapshot and reads only
the corresponding variables. Every target must exist in the current Flowable tenant and have the current
ADMIN user as its starter. All values are validated before the instance starts. The new instance and its
frozen `bpm_process_instance_relation` rows commit or roll back together.

External start subjects cannot launch a definition containing this component. Deployment rejects any model
that makes the component writable on a task node. Existing instances are not backfilled, and cancellation,
rejection, return or later resubmission never mutates a previous instance's frozen relations.

## Read APIs

- `GET /admin-api/bpm/process-instance/relation-candidate-page`: requires
  `bpm:process-instance:create`; returns only the current ADMIN user's started instances and supports
  `pageNo`, `pageSize`, `keyword`, `processDefinitionKey`, `status` and `startTime`.
- `GET /admin-api/bpm/process-instance/relation-list?processInstanceId=...`: requires
  `bpm:process-instance:query`; returns frozen relations grouped by `formField`.
- `GET /admin-api/bpm/process-instance/relation-detail?relationId=...`: returns the direct target's frozen
  summary plus read-only form, model view and approval trajectory when Flowable history remains available.
- `GET /admin-api/bpm/process-instance/relation-print-data?relationId=...`: returns the same frozen summary
  and the established process print projection.

Relation reads require the caller to be an actual participant of the source: ADMIN starter, historic or
current task assignee/owner, or persisted copy recipient. Candidate-only users are not participants. This
object grant is direct and read-only; A-to-B does not grant access to B-to-C, and no target approval,
cancellation, transfer, countersign, comment or mutation endpoint accepts it as authorization.

`displayNo` is returned only when the target definition enabled the BPM process-number rule. Internal
instance IDs remain transport identifiers and are not used as a visible fallback. If target history has
been cleaned, list/detail responses keep the frozen summary and set `detailAvailable=false` without
inventing a current status or trajectory.
