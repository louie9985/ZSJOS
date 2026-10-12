package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import lombok.Data;

@Data
public class LeadAppealReviewDetailRespVO {
    private LeadManagementRespVO lead;
    private LeadAppealContextRespVO context;
}
