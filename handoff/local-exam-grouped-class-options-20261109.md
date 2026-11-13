# Grouped exam collection class options

- Environment local, main at 7a7cf5e14b, D:/ZSJ-OS. Owner /root/backend_acceptance under /root coordination.
- Goal expose authoritative class group data for multi-class notification tree. Ownership ExamCollectionRosterService.java, ExamCollectionVO.java, new focused class option test and this record. Frontend owned by frontend_acceptance.
- Reuse DeliveryClassDO.groupId and DeliveryClassGroupDO name/createTime/id. Group order follows existing group creation-time descending/id descending, grouped options first; children retain class ID descending. Ungrouped classes stay independent options, empty groups omitted.
- Existing SERVING/assignable filter and notify planner enabled validation remain. Submitted IDs are classIds. No menu/data scope change or group inference from names.
- Preserve earlier pending Member/WeChat and unrelated delivery-class changes. No commit, migration, database writes or service restart. Verification isolated Java tests and scoped diff.

## Delivery
- ClassOption keeps id/name/plannerId/plannerName and adds nullable groupId/groupName. Authoritative membership is DeliveryClassDO.groupId; group label and ordering use DeliveryClassGroupDO, mirroring DeliveryClassGroupService's createTime/id descending policy. Group lookup is batched only for represented serving assignable classes.
- Grouped options precede independent ungrouped classes; stable class order remains id descending within groups. Empty groups are omitted, unavailable/deleted/cross-tenant referenced groups reject instead of silently becoming ungrouped. Existing notify command still accepts classIds and validates current serving class/planner availability.
- Coordinated frontend group:<id>/class:<id> tree identity, same-name group separation and independent ungrouped leaves. No label-based inference or group IDs in notification payload.
- Added three regression tests covering authoritative same-name groups, group ordering, batch lookup, pending-class exclusion, independent/empty results and tenant/menu denial. Current-source isolated compile and 124/124 Java tests passed, 27 containers, zero skips/failures. Scoped diff check passed.
- No backend runtime restarted, database changed, staging or commit performed. Frontend browser verification remains its owner's scope. Backend ownership released.
