package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;

import java.util.List;

public interface LeadSubmissionService {
    LeadCreateRespVO create(LeadCreateReqVO reqVO, Long submitterUserId);
    LeadCreateRespVO createForPartner(LeadCreateReqVO reqVO, Long accountId, Long partnerId);
    LeadCreateRespVO createSelfSourced(LeadCreateReqVO reqVO, Long salesUserId);
    List<LeadAssignmentUserRespVO> getNewMediaProviders();
    List<LeadAssignmentUserRespVO> getSpecifiedSalesUsers(Long operatorUserId);
}
