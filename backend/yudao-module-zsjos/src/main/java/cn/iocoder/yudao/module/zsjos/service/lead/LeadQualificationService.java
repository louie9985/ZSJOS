package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.*;

import java.util.List;

public interface LeadQualificationService {
    void judgeValid(Long leadId, Long userId, LeadQualificationCommandReqVO reqVO);
    void judgeInvalid(Long leadId, Long userId, LeadJudgeInvalidReqVO reqVO);
    PageResult<LeadQualificationExceptionRespVO> getExceptionPage(LeadQualificationExceptionPageReqVO reqVO,
                                                                  Long userId);
    List<LeadAssignmentUserRespVO> getTransferCandidates(Long leadId, Long userId);
    void restore(Long leadId, Long userId, LeadDispositionReqVO reqVO);
    void transfer(Long leadId, Long userId, LeadTransferReqVO reqVO);
    void recycle(Long leadId, Long userId, LeadDispositionReqVO reqVO);
    void releaseToClaimPool(Long leadId, Long userId, LeadDispositionReqVO reqVO);
    int processExpired();
}
