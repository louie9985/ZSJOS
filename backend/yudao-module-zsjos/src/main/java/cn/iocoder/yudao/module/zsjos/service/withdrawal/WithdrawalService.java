package cn.iocoder.yudao.module.zsjos.service.withdrawal;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

public interface WithdrawalService {
    Long apply(Long userId, WithdrawalApplyReqVO request);
    void cancel(Long id, Long userId);
    void rejectApproved(Long id, Long userId, String reason);
    void recordPayout(Long id, Long userId, WithdrawalPayoutReqVO request);
    void handleProcessResult(String processInstanceId, Integer processStatus, String reason);
    PageResult<WithdrawalRespVO> getPage(WithdrawalPageReqVO request, Long applicantUserId);
    WithdrawalRespVO getDetail(Long id, Long userId, boolean fullCard);
    List<BankCardRespVO> getMyCards(Long userId);
    LeadAttachmentUploadRespVO uploadProof(Long userId, MultipartFile file) throws IOException;
}
