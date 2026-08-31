package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;

import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SUBORDINATE_LEAD_STATE_INVALID;

/**
 * Supervisor Lead commands share one state policy so action projection and mutation validation cannot drift.
 */
public final class SupervisorLeadActionPolicy {

    private SupervisorLeadActionPolicy() {
    }

    public enum Action {
        TRANSFER("转派", "“已提交、已挂起、有效或已转化 / 已归属或回收待处理”的客资"),
        RESTORE("恢复", "“已挂起 / 已归属”的客资"),
        RECYCLE("回收", "“已提交或已挂起 / 已归属”的客资"),
        RELEASE_CLAIM_POOL("释放至抢单池", "“已提交或已挂起 / 已归属或回收待处理”的客资"),
        RELEASE_PUBLIC_SEA("释放至公海池", "“有效或已转化 / 已归属且未关闭”的客资");

        private final String label;
        private final String requirement;

        Action(String label, String requirement) {
            this.label = label;
            this.requirement = requirement;
        }
    }

    public static boolean isAllowed(Action action, LeadDO lead) {
        return switch (action) {
            case TRANSFER -> Set.of(STATUS_SUBMITTED, STATUS_SUSPENDED, STATUS_VALID, STATUS_CONVERTED)
                    .contains(lead.getStatus())
                    && Set.of(ASSIGNMENT_OWNED, ASSIGNMENT_RECYCLE_PENDING).contains(lead.getAssignmentStatus());
            case RESTORE -> STATUS_SUSPENDED.equals(lead.getStatus())
                    && ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus());
            case RECYCLE -> Set.of(STATUS_SUBMITTED, STATUS_SUSPENDED).contains(lead.getStatus())
                    && ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus());
            case RELEASE_CLAIM_POOL -> Set.of(STATUS_SUBMITTED, STATUS_SUSPENDED).contains(lead.getStatus())
                    && Set.of(ASSIGNMENT_OWNED, ASSIGNMENT_RECYCLE_PENDING).contains(lead.getAssignmentStatus());
            case RELEASE_PUBLIC_SEA -> Set.of(STATUS_VALID, STATUS_CONVERTED).contains(lead.getStatus())
                    && ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus()) && lead.getClosedAt() == null;
        };
    }

    public static void requireAllowed(Action action, LeadDO lead) {
        if (isAllowed(action, lead)) return;
        throw exception(SUBORDINATE_LEAD_STATE_INVALID, action.label, describeCurrentState(lead),
                action.label, action.requirement);
    }

    static String describeCurrentState(LeadDO lead) {
        String state = leadStatusLabel(lead.getStatus()) + " / " + assignmentStatusLabel(lead.getAssignmentStatus());
        return lead.getClosedAt() == null ? state : state + "，已设置关闭时间";
    }

    static String leadStatusLabel(String status) {
        if (status == null) return "未知客资状态（空）";
        return switch (status) {
            case STATUS_SUBMITTED -> "已提交";
            case STATUS_SUSPENDED -> "已挂起";
            case STATUS_VALID -> "有效";
            case STATUS_CONVERTED -> "已转化";
            case STATUS_INVALID -> "无效";
            case STATUS_WON -> "已成交";
            case STATUS_CLOSED -> "已关闭";
            default -> "未知客资状态（" + status + "）";
        };
    }

    static String assignmentStatusLabel(String status) {
        if (status == null) return "未知分配状态（空）";
        return switch (status) {
            case ASSIGNMENT_UNASSIGNED -> "未分配";
            case ASSIGNMENT_PENDING -> "待接单";
            case ASSIGNMENT_OWNED -> "已归属";
            case ASSIGNMENT_PUBLIC_POOL -> "抢单池";
            case ASSIGNMENT_RECYCLE_PENDING -> "回收待处理";
            case ASSIGNMENT_CLOSED -> "已关闭";
            default -> "未知分配状态（" + status + "）";
        };
    }
}
