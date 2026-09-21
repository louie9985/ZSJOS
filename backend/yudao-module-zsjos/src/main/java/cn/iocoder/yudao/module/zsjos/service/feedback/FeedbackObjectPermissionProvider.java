package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackRoundDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackRoundMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.SUBJECT_ADMIN;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TYPE_PERMISSION;

/**
 * 反馈单据的对象级权限。
 *
 * <p>{@code read-own} 与 {@code read-approver} 刻意分开，不合并成一个宽松的读权限：
 * 前者是「这是我的单子」，后者是「我是这一轮的指定审批人」。审批人不是单据本人，
 * 混进 {@code read-own} 之后这个 action 的名字就没法解释了，后续接权限的人会误判。
 */
@Component
public class FeedbackObjectPermissionProvider implements ZsjosObjectPermissionProvider {

    /** 审批人查看：只看他参与过的那几轮。 */
    public static final String ACTION_READ_APPROVER = "read-approver";

    @Resource
    private FeedbackMapper feedbackMapper;
    @Resource
    private FeedbackRoundMapper roundMapper;
    @Resource
    private PermissionApi permissionApi;

    @Override
    public String getBizType() {
        return "feedback";
    }

    @Override
    public boolean hasPermission(Long feedbackId, String action, Long userId) {
        FeedbackDO feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) return false;
        if (("read-own".equals(action) || ACTION_READ_APPROVER.equals(action))
                && permissionApi.hasTenantReadAllAccess(userId)) return true;
        if (action.endsWith("-own")) {
            return SUBJECT_ADMIN.equals(feedback.getSubmitterSubjectType())
                    && Objects.equals(feedback.getSubmitterUserId(), userId);
        }
        if (ACTION_READ_APPROVER.equals(action)) {
            return isApproverOfAnyRound(feedbackId, userId);
        }
        if ("manage".equals(action) || "read-admin".equals(action)) {
            String permission = TYPE_PERMISSION.get(feedback.getFeedbackType());
            return permission != null && permissionApi.hasAnyPermissions(userId, permission);
        }
        return false;
    }

    /**
     * 该用户是不是这条反馈**任一轮次**的指定审批人。
     *
     * <p>遍历所有轮次而不是只看最新一轮：多轮审批时历史轮次的审批人回来翻单子，
     * 仍然应该看得到——他当时确实审过。判定完全依赖提交时冻结的
     * {@code approval_context_json}，不看角色、不看部门负责人字段的当前值，
     * 否则人事变动会让历史审批记录对不上。
     */
    private boolean isApproverOfAnyRound(Long feedbackId, Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            List<FeedbackRoundDO> rounds = roundMapper.selectByFeedbackId(feedbackId);
            if (rounds == null) {
                return false;
            }
            return rounds.stream()
                    .anyMatch(round -> FeedbackApprovalContext.isApprover(
                            FeedbackApprovalContext.parse(round), userId));
        } catch (Exception ex) {
            // 查不到轮次就不放行：这里宁可少放行，也不能把单据暴露给非审批人。
            return false;
        }
    }

    @Override
    public void check(Long bizId, String action, Long userId) {
        if (!hasPermission(bizId, action, userId)) throw exception(FEEDBACK_PERMISSION_DENIED);
    }
}
