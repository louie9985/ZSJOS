package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.duplicate.LeadDuplicateReviewDecisionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.duplicate.LeadDuplicateReviewPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.duplicate.LeadDuplicateReviewRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateRespVO;

import java.util.List;

public interface LeadDuplicateReviewService {
    PageResult<LeadDuplicateReviewRespVO> getPage(LeadDuplicateReviewPageReqVO request);
    LeadDuplicateReviewRespVO get(Long id);
    List<LeadAssignmentUserRespVO> getSalesCandidates(Long reviewerUserId);
    void decide(Long id, LeadDuplicateReviewDecisionReqVO request, Long reviewerUserId);
    LeadCreateRespVO resolveAutomatically(Long id, Long matchedLeadId, Long actorUserId);
}
