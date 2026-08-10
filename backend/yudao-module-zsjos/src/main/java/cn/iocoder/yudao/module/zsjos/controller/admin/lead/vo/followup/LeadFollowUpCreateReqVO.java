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
    @NotBlank @Size(max = 100) private String method;
    @NotBlank @Size(max = 100) private String result;
    @NotBlank @Size(max = 100) private String leadCategory;
    @Size(max = 2000) private String remark;
    private LocalDateTime nextFollowUpAt;
    @Valid @Size(max = 9) private List<LeadAttachmentReqVO> images = new ArrayList<>();
    @NotBlank @Size(max = 64) private String idempotencyKey;
}
