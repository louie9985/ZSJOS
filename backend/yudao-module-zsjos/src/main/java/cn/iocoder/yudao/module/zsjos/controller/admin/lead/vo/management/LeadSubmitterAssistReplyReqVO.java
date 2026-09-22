package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class LeadSubmitterAssistReplyReqVO {
    @NotBlank @Size(max = 2000)
    private String remark;
    @Size(max = 20)
    private List<@Valid LeadAttachmentReqVO> attachments;
    @NotNull
    private Integer version;
    @NotBlank @Size(max = 128)
    private String idempotencyKey;
}
