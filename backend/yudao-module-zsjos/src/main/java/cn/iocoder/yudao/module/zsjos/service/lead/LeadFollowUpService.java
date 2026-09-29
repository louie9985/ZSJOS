package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpRespVO;

public interface LeadFollowUpService {
    LeadFollowUpRespVO create(Long leadId, Long operatorUserId, LeadFollowUpCreateReqVO reqVO);
    LeadFollowUpRespVO createSelfSourcedAutomatic(Long leadId, Long operatorUserId, java.time.LocalDateTime nextFollowUpAt);
    PageResult<LeadFollowUpRespVO> getPage(Long leadId, int pageNo, int pageSize);

    PageResult<LeadFollowUpRespVO> getPage(Long leadId, int pageNo, int pageSize, Long viewerId);
}
