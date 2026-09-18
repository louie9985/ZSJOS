package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackRoundDO;

import java.util.Map;

/**
 * 反馈审批上下文快照的读取口径。
 *
 * <p>提交需求/反馈时，{@code FeedbackServiceImpl.resolveApprovalContext} 会把本轮的
 * 部门负责人与董事长写进 {@code zsjos_feedback_round.approval_context_json}。这份快照
 * 有两个消费者，且**必须口径一致**，否则会出现「审批人能打开详情页、却在页面里看不到内容」
 * 这种自相矛盾的状态：
 * <ul>
 *   <li>{@code FeedbackContentProvider} —— 审批中心那张业务卡能不能渲染；</li>
 *   <li>{@code FeedbackObjectPermissionProvider} —— {@code /zsjos/feedback/{id}} 放不放行。</li>
 * </ul>
 *
 * <p>因此键名与解析逻辑都收敛到这里，不要在两处各写一份。
 */
public final class FeedbackApprovalContext {

    public static final String DEPARTMENT_LEADER_USER_ID = "departmentLeaderUserId";
    public static final String CHAIRMAN_USER_ID = "chairmanUserId";
    /** 姓名是给界面看的，这里一并声明，避免各处拼错键名。 */
    public static final String DEPARTMENT_LEADER_NAME = "departmentLeaderName";
    public static final String CHAIRMAN_NAME = "chairmanName";

    private FeedbackApprovalContext() {
    }

    /**
     * 解析轮次的审批人快照。
     *
     * <p>解析失败一律按「无审批人」处理：宁可不放行，也不要把整条单据
     * 暴露给解析异常波及的人。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(FeedbackRoundDO round) {
        if (round == null || round.getApprovalContextJson() == null
                || round.getApprovalContextJson().isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = JsonUtils.parseObject(round.getApprovalContextJson(), Map.class);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    /**
     * 快照里记录的某人是不是本轮指定审批人（部门负责人或董事长）。
     */
    public static boolean isApprover(Map<String, Object> context, Long userId) {
        if (context == null || context.isEmpty() || userId == null) {
            return false;
        }
        return matches(context.get(DEPARTMENT_LEADER_USER_ID), userId)
                || matches(context.get(CHAIRMAN_USER_ID), userId);
    }

    /** 快照里该审批人的姓名；没有记录时返回 null。 */
    public static String approverName(Map<String, Object> context, String nameKey) {
        Object value = context.get(nameKey);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean matches(Object value, Long userId) {
        if (value == null) {
            return false;
        }
        try {
            return Long.valueOf(String.valueOf(value)).equals(userId);
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
