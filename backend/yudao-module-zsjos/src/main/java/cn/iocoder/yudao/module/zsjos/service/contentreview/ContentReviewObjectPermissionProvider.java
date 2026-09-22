package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.ArrayDeque;
import java.util.HashSet;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CONTENT_REVIEW_PERMISSION_DENIED;

@Component
public class ContentReviewObjectPermissionProvider implements ZsjosObjectPermissionProvider {

    @Resource private ContentReviewBatchMapper batchMapper;
    @Resource private PermissionApi permissionApi;
    @Resource private ContentReviewAccessService accessService;

    @Override
    public String getBizType() {
        return "content-review-batch";
    }

    @Override
    public boolean hasPermission(Long batchId, String action, Long userId) {
        ContentReviewBatchDO batch = batchMapper.selectById(batchId);
        if (batch == null || userId == null) return false;
        if (!Objects.equals(batch.getTenantId(), TenantContextHolder.getTenantId())) return false;
        if ("read".equals(action)) return canReadDirectly(batch, userId) || canReadSubmittedAncestor(batch, userId);
        // A superseded round is immutable, even for its original operator/reviewer.
        if (!batchMapper.selectByRevisionOfBatchId(batchId).isEmpty()) return false;
        return switch (action) {
            case "submit", "publish" -> Objects.equals(batch.getOperatorUserId(), userId);
            case "director-review" -> Objects.equals(batch.getDirectorUserId(), userId);
            case "final-review" -> accessService.hasCurrentTask(batch, userId);
            default -> false;
        };
    }

    private boolean canReadDirectly(ContentReviewBatchDO batch, Long userId) {
        return permissionApi.hasTenantReadAllAccess(userId)
                || Objects.equals(batch.getOperatorUserId(), userId)
                || Objects.equals(batch.getDirectorUserId(), userId)
                || permissionApi.hasAnyPermissions(userId, "zsjos:content-review:query-all")
                || accessService.hasCurrentTask(batch, userId)
                || accessService.hasReviewedHistory(batch.getId(), userId);
    }

    private boolean canReadSubmittedAncestor(ContentReviewBatchDO batch, Long userId) {
        if (batch.getSubmittedAt() == null) return false;
        var queue = new ArrayDeque<ContentReviewBatchDO>();
        var visited = new HashSet<Long>();
        queue.add(batch);
        while (!queue.isEmpty()) {
            ContentReviewBatchDO parent = queue.removeFirst();
            if (!visited.add(parent.getId())) continue;
            for (ContentReviewBatchDO child : batchMapper.selectByRevisionOfBatchId(parent.getId())) {
                if (!Objects.equals(child.getTenantId(), batch.getTenantId())
                        || !Objects.equals(child.getRevisionOfBatchId(), parent.getId())
                        || visited.contains(child.getId())) continue;
                // Never call hasPermission recursively: inherited reads cannot become new grants.
                if (canReadDirectly(child, userId)) return true;
                queue.addLast(child);
            }
        }
        return false;
    }

    @Override
    public void check(Long batchId, String action, Long userId) {
        if (!hasPermission(batchId, action, userId)) throw exception(CONTENT_REVIEW_PERMISSION_DENIED);
    }
}
