package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SubordinateSalesRespVO {
    private Long userId;
    private String name;
    private String avatar;
    private String username;
    private String mobile;
    private Integer accountStatus;
    private String presence;
    private Boolean accepting;
    private Boolean eligible;
    private Boolean canReceiveNewLeads;
    private String newcomerPoolStatus;
    private Long todayPendingCount;
    private Long todayFollowUpTotalCount;
    private Long todayFollowUpRemainingCount;
    private Long todayAssignedCount;
    private Long todayMissedCount;
    private Long todayReceivedCount;
    private Long todayQualifiedCount;
    private Long todayFollowUpRecordCount;
    private BigDecimal todayOrderAmount;
    private Long pendingQualificationCount;
    private String todayFollowUpStatus;
    private Long firstFollowTimeoutCount;
    private Long suspendedLeadCount;
    private List<CategoryCountVO> categoryCounts;
    private Long validLeadCount;
    private Long convertedLeadCount;
    private Long effectiveOrderCount;
    private BigDecimal effectiveOrderAmount;

    @Data
    public static class CategoryCountVO {
        private String value;
        private String label;
        private Long count;
        private Boolean configured;
    }
}
