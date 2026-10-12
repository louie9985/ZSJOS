package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

/** A cycle is the immutable invalid decision, not ownership, an appeal round or a mutable timestamp. */
public final class LeadAppealCycles {
    public static final cn.iocoder.yudao.framework.common.exception.ErrorCode STALE =
            new cn.iocoder.yudao.framework.common.exception.ErrorCode(1_900_003_181, "无效判定已变化，请刷新后针对当前判定重新申诉");
    private LeadAppealCycles() {}
    public static long key(LeadAppealDO appeal) { return appeal.getInvalidEventId() == null ? 0 : appeal.getInvalidEventId(); }
    public static boolean active(LeadAppealDO appeal) {
        return appeal.getStatus() != null && Set.of(APPEAL_STATUS_SALES_MANAGER_REVIEWING, APPEAL_STATUS_QUALITY_REVIEWING,
                APPEAL_STATUS_CHAIRMAN_REVIEWING, "pending").contains(appeal.getStatus());
    }
    public static List<LeadAppealDO> current(List<LeadAppealDO> history, long eventId) {
        return history.stream().filter(a -> key(a) == eventId).sorted(Comparator.comparing(LeadAppealDO::getRoundNo)).toList();
    }
    public static boolean unresolvedHistory(List<LeadAppealDO> history, long eventId) {
        return history.stream().anyMatch(a -> active(a) || eventId != 0 && key(a) == 0);
    }
    public static Integer nextRound(List<LeadAppealDO> history, long eventId) {
        if (unresolvedHistory(history, eventId)) return null;
        var rows = current(history, eventId);
        if (rows.isEmpty()) return 1;
        var last = rows.getLast();
        return APPEAL_STATUS_UPHELD.equals(last.getStatus()) && last.getRoundNo() < 3 ? last.getRoundNo() + 1 : null;
    }
}
