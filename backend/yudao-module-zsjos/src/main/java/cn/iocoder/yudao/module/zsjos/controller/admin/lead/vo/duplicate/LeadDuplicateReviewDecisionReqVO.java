package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.duplicate;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LeadDuplicateReviewDecisionReqVO {
    @NotBlank @Pattern(regexp = "new_person|reuse_person|reactivate_lead|notify_owner")
    private String resultType;
    private Long matchedPersonId;
    private Long matchedLeadId;
    private Long selectedSalesUserId;
    @NotBlank @Size(max = 2000) private String opinion;
    @Valid @Size(max = 9) private List<LeadAttachmentReqVO> attachments = new ArrayList<>();
    @NotBlank @Size(max = 128) private String idempotencyKey;
}
