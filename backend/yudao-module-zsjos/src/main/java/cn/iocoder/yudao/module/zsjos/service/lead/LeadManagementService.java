package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadInboxFilterProfileRespVO;

public interface LeadManagementService {

    PageResult<LeadManagementRespVO> getLeadPage(LeadManagementPageReqVO reqVO, Long userId);

    LeadManagementRespVO getLead(Long id, Long userId);

    java.util.Map<String, Long> getStatusCounts(Long userId);

    LeadInboxFilterProfileRespVO getInboxFilterProfile(Long userId, String audience);
}
