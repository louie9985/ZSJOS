package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewSubmitContextRespVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewSelectionErrors.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CONTENT_REVIEW_PERMISSION_DENIED;

@Service
public class ContentReviewSelectionService {
    public static final String SOURCE = "STARTER_ROLE_SELECTION";
    public static final List<String> READ_PERMISSIONS = List.of("zsjos:content-review:query",
            "zsjos:content-review:director-review", "zsjos:media-student:query-my",
            "zsjos:media-account:query", "zsjos:positioning-card:query",
            "zsjos:student:positioning-interview-query");
    @Resource private ContentReviewBatchMapper batchMapper;
    @Resource private ContentReviewConfigService configService;
    @Resource private BpmDefinitionReadApi definitionApi;
    @Resource private AdminUserApi userApi;
    @Resource private PermissionApi permissionApi;

    public boolean hasReadPermissions(Long userId) {
        return READ_PERMISSIONS.stream().allMatch(p -> permissionApi.hasAnyPermissions(userId, p));
    }

    @ZsjosPermission(bizType="content-review-batch", bizId="#batchId", action="submit")
    public ContentReviewSubmitContextRespVO context(Long batchId, Long userId) {
        var batch = batchMapper.selectById(batchId);
        if (batch == null || !Objects.equals(batch.getOperatorUserId(), userId)
                || !"DRAFT".equals(batch.getStatus())) throw exception(CONTENT_REVIEW_PERMISSION_DENIED);
        var definition = configService.requireCurrentDefinition(configService.requireReadyConfig(userId));
        var keys = configService.requireReviewTaskKeys(definition);
        var scope = definitionApi.getStarterSelectionScope(definition.getId(), keys.director());
        if (scope == null || scope.getRoleIds().isEmpty()) throw exception(ROLE_SCOPE_REQUIRED);
        var result = new ContentReviewSubmitContextRespVO();
        result.setProcessDefinitionId(definition.getId());
        result.setProcessDefinitionVersion(definition.getVersion());
        result.setDirectorTaskKey(keys.director()); result.setRoleIds(scope.getRoleIds());
        result.setCandidates(scope.getUserIds().isEmpty() ? List.of() : userApi.getUserList(scope.getUserIds()).stream()
                .filter(u -> scope.getUserIds().contains(u.getId()) && !Objects.equals(u.getId(), userId)
                        && cn.iocoder.yudao.framework.common.enums.CommonStatusEnum.isEnable(u.getStatus()) && hasReadPermissions(u.getId()))
                .map(u -> new ContentReviewSubmitContextRespVO.Candidate(u.getId(), u.getNickname())).toList());
        if (batch.getRevisionOfBatchId() != null) {
            var previous = batchMapper.selectById(batch.getRevisionOfBatchId());
            if (previous != null) result.setPreviousDirectorUserId(previous.getDirectorUserId());
        }
        return result;
    }
}
