package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import lombok.Data;

@Data
public class PartnerLeadFollowUpSummaryRespVO {
    private long followUpPendingCount;
    private long unreachableCount;
    private long invalidCount;
}
