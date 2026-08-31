package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class LeadSubmitterAssistRequestReqVO {

    @NotBlank
    @Size(max = 1000)
    private String problem;

    @NotBlank
    @Size(max = 1000)
    private String expectedAssistance;

    @Size(max = 2000)
    private String remark;

    @Size(max = 9)
    private List<@Valid LeadAttachmentReqVO> attachments;

    @NotBlank
    @Size(max = 128)
    private String idempotencyKey;
}
