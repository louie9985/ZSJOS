package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.CursorPageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface LeadAppealService {
    List<LeadAppealRespVO> getLeadAppeals(Long leadId, Long userId);
    List<LeadAppealRespVO> getPartnerLeadAppeals(Long leadId, Long partnerId);
    Long submit(Long leadId, Long userId, LeadAppealSubmitReqVO reqVO);
    Long submitForPartner(Long leadId, Long accountId, Long partnerId, LeadAppealSubmitReqVO reqVO);
    PageResult<LeadAppealRespVO> getInboxPage(LeadAppealPageReqVO reqVO, Long userId);
    CursorPageResult<LeadAppealRespVO> getInboxCursor(LeadAppealPageReqVO reqVO, Long userId);
    void overturn(Long appealId, Long userId, LeadAppealDecisionReqVO reqVO);
    void uphold(Long appealId, Long userId, LeadAppealDecisionReqVO reqVO);
    LeadAttachmentUploadRespVO upload(MultipartFile file) throws IOException;
    LeadAttachmentUploadRespVO uploadForPartner(MultipartFile file, Long accountId) throws IOException;
}
