package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewSelectionErrors.REVIEW_READ_EXPIRED;

/** Grants no general student permission: every projection binds its batch and exact resource. */
@Component
public class ContentReviewStudentAccess implements ZsjosObjectPermissionProvider {
    @Resource private ContentReviewBatchMapper batches;
    @Resource private MediaAccountMapper accounts;
    @Resource private ServiceRelationMapper relations;
    @Resource private ContentReviewSelectionService selection;
    @Resource private BpmProcessInstanceApi processes;
    public record Scope(Long personId, List<MediaAccountDO> accounts) { }

    public Scope require(Long batchId, Long userId) {
        var batch = batches.selectById(batchId);
        if (batch == null || userId == null || !Objects.equals(batch.getTenantId(), TenantContextHolder.getTenantId())
                || !Objects.equals(batch.getDirectorUserId(), userId) || batch.getStudentPersonId() == null
                || batch.getSubmittedAt() == null || batch.getProcessInstanceId() == null
                || !Set.of("DIRECTOR_REVIEW", "FINAL_REVIEW").contains(batch.getStatus())
                || !selection.hasReadPermissions(userId)) throw exception(REVIEW_READ_EXPIRED);
        Map<String,Object> reviewer = JsonUtils.parseMap(batch.getRelationSnapshotJson());
        if (reviewer == null || !ContentReviewSelectionService.SOURCE.equals(reviewer.get("source"))
                || !processes.isRunning(batch.getProcessInstanceId(), batch.getBusinessKey())) throw exception(REVIEW_READ_EXPIRED);
        Map<String,Object> context = JsonUtils.parseMap(batch.getContextSnapshotJson());
        if (context == null || !(context.get("reviewAccountScopes") instanceof List<?> frozen) || frozen.isEmpty())
            throw exception(REVIEW_READ_EXPIRED);
        List<MediaAccountDO> result = new ArrayList<>();
        for (Object item : frozen) {
            if (!(item instanceof Map<?,?> saved)) throw exception(REVIEW_READ_EXPIRED);
            var account = accounts.selectById(number(saved.get("accountId")));
            if (account == null || !Objects.equals(account.getTenantId(), batch.getTenantId())
                    || !Objects.equals(account.getStudentPersonId(), batch.getStudentPersonId())
                    || !Objects.equals(account.getCreateServiceRelationId(), number(saved.get("serviceRelationId")))
                    || !Objects.equals(account.getDirectorUserId(), number(saved.get("directorUserId")))
                    || !Objects.equals(account.getOwnerOperatorUserId(), number(saved.get("operatorUserId"))))
                throw exception(REVIEW_READ_EXPIRED);
            if (account.getCreateServiceRelationId() != null) {
                var relation = relations.selectById(account.getCreateServiceRelationId());
                if (relation == null || !Objects.equals(relation.getPersonId(), batch.getStudentPersonId())
                        || !Objects.equals(relation.getTenantId(), batch.getTenantId())
                        || !Objects.equals(relation.getCollaborationGroupId(), number(saved.get("collaborationGroupId")))
                        || !Objects.equals(relation.getContentDirectorUserId(), account.getDirectorUserId())
                        || !Objects.equals(relation.getOperatorUserId(), account.getOwnerOperatorUserId())) throw exception(REVIEW_READ_EXPIRED);
            }
            result.add(account);
        }
        return new Scope(batch.getStudentPersonId(), List.copyOf(result));
    }

    public MediaAccountDO requireAccount(Long batchId, Long accountId, Long userId) {
        return require(batchId, userId).accounts().stream().filter(a -> Objects.equals(a.getId(), accountId))
                .findFirst().orElseThrow(() -> exception(REVIEW_READ_EXPIRED));
    }
    public void requireService(Long batchId, Long relationId, Long userId) {
        if (relationId == null || require(batchId, userId).accounts().stream()
                .noneMatch(a -> Objects.equals(a.getCreateServiceRelationId(), relationId))) throw exception(REVIEW_READ_EXPIRED);
    }
    public List<Map<String,Object>> freeze(List<Long> accountIds) {
        return accountIds.stream().map(id -> {
            var a = accounts.selectById(id);
            Map<String,Object> value = new LinkedHashMap<>();
            value.put("accountId", a.getId()); value.put("serviceRelationId", a.getCreateServiceRelationId());
            value.put("directorUserId", a.getDirectorUserId()); value.put("operatorUserId", a.getOwnerOperatorUserId());
            var r = a.getCreateServiceRelationId() == null ? null : relations.selectById(a.getCreateServiceRelationId());
            value.put("collaborationGroupId", r == null ? null : r.getCollaborationGroupId());
            return value;
        }).toList();
    }
    private static Long number(Object value) { return value instanceof Number n ? n.longValue() : null; }
    @Override public String getBizType() { return "content-review-student"; }
    @Override public boolean hasPermission(Long id, String action, Long userId) {
        if (!"read".equals(action)) return false;
        try { require(id, userId); return true; }
        catch (cn.iocoder.yudao.framework.common.exception.ServiceException e) { return false; }
    }
    @Override public void check(Long id, String action, Long userId) {
        if (!"read".equals(action)) throw exception(REVIEW_READ_EXPIRED);
        require(id, userId);
    }
}
