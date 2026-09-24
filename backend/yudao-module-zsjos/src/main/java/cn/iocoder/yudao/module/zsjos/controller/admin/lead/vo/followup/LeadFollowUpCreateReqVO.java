package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class LeadFollowUpCreateReqVO {
    @Size(max = 100) private String salesStage;
    @NotBlank @Size(max = 100) private String method;
    @NotBlank @Size(max = 100) private String result;
    @Size(max = 100) private String leadCategory;
    @NotBlank @Size(max = 2000) private String remark;
    // Required for pre-deal follow-up; the service validates against the locked Lead state.
    private LocalDateTime nextFollowUpAt;
    @Valid @Size(max = 9) private List<LeadAttachmentReqVO> images = new ArrayList<>();
    @NotBlank @Size(max = 64) private String idempotencyKey;
}
