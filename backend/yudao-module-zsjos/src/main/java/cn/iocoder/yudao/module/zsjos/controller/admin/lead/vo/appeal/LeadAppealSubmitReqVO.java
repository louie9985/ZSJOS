package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LeadAppealSubmitReqVO {
    @NotBlank @Size(max = 1000) private String reason;
    @NotBlank @Size(max = 100) private String idempotencyKey;
    @Valid @Size(max = 9) private List<LeadAttachmentReqVO> attachments = new ArrayList<>();
}
