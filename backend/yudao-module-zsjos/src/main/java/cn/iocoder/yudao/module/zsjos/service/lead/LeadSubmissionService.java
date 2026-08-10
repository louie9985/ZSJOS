package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateRespVO;

public interface LeadSubmissionService {
    LeadCreateRespVO create(LeadCreateReqVO reqVO, Long submitterUserId);
}
