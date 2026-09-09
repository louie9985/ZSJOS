package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

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
        boolean operator = Objects.equals(batch.getOperatorUserId(), userId);
        boolean director = Objects.equals(batch.getDirectorUserId(), userId);
        boolean queryAll = permissionApi.hasAnyPermissions(userId, "zsjos:content-review:query-all");
        boolean currentReviewer = accessService.hasCurrentTask(batch, userId);
        boolean historicalReviewer = accessService.hasReviewedHistory(batchId, userId);
        return switch (action) {
            case "read" -> operator || director || currentReviewer || historicalReviewer || queryAll;
            case "submit", "publish" -> operator;
            case "director-review" -> director;
            case "final-review" -> currentReviewer;
            default -> false;
        };
    }

    @Override
    public void check(Long batchId, String action, Long userId) {
        if (!hasPermission(batchId, action, userId)) throw exception(CONTENT_REVIEW_PERMISSION_DENIED);
    }
}
