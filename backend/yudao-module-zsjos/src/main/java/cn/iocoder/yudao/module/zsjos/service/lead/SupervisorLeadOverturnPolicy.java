package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import java.util.Arrays;
import java.util.List;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

/** One eligibility contract for the projected action and the locked command. */
public final class SupervisorLeadOverturnPolicy {
    private SupervisorLeadOverturnPolicy() {}
    public static final String PERMISSION = "zsjos:subordinate-sales:lead-overturn-valid";
    public static final String ACTION = "SUPERVISOR_OVERTURN_VALID";
    public static final String EVENT = "lead_supervisor_overturned";
    public static final String SCENE = "zsjos.lead.supervisor_overturned";
    public static final String OBJECT_ACTION = "supervisor-overturn-valid";
    public static final ErrorCode STATE_INVALID = new ErrorCode(1_900_003_132, "仅可将已归属的无效客资改判有效");
    public static final ErrorCode APPEAL_BLOCKED = new ErrorCode(1_900_003_133, "该客资存在申诉处理或高层复核记录，请通过原申诉流程处理");
    public static final ErrorCode STALE = new ErrorCode(1_900_003_134, "客资归属或无效判定已变化，请刷新后重试");
    public static final ErrorCode OPPORTUNITY_INVALID = new ErrorCode(1_900_003_135, "商机状态异常，不能直接改判有效");

    public static ErrorCode rejection(LeadDO lead, List<LeadAppealDO> appeals, OpportunityDO opportunity) {
        if (!STATUS_INVALID.equals(lead.getStatus()) || !ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())
                || lead.getOwnerUserId() == null || lead.getClosedAt() != null) return STATE_INVALID;
        if (!appeals.isEmpty() && !(appeals.size() == 1 && Integer.valueOf(1).equals(appeals.getFirst().getRoundNo())
                && APPEAL_STATUS_UPHELD.equals(appeals.getFirst().getStatus())
                && APPEAL_STAGE_SALES_MANAGER.equals(appeals.getFirst().getReviewStage()))) return APPEAL_BLOCKED;
        if (opportunity != null && (!OPPORTUNITY_TYPE_INITIAL_CONVERSION.equals(opportunity.getType())
                || !OPPORTUNITY_STATUS_LOST.equals(opportunity.getStatus()) || opportunity.getWonAt() != null)) {
            return OPPORTUNITY_INVALID;
        }
        return null;
    }

    public static String token(LeadDO lead, Long invalidEventId) {
        // Event identity distinguishes repeated identical invalid decisions even at the same timestamp.
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(lead.getId(), lead.getVersion(),
                lead.getOwnerUserId(), lead.getQualificationRoundNo(), lead.getQualifiedAt(), invalidEventId,
                lead.getInvalidReason(), lead.getInvalidDescription(), lead.getInvalidEvidenceRefs())));
    }
}
