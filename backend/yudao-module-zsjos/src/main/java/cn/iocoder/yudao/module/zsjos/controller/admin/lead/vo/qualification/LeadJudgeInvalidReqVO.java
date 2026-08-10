package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeadJudgeInvalidReqVO extends LeadQualificationCommandReqVO {
    @NotBlank
    @Size(max = 100)
    private String reasonCode;

    @NotBlank
    @Size(max = 2000)
    private String description;

    @Valid
    @Size(max = 9)
    private List<LeadAttachmentReqVO> attachments = new ArrayList<>();
}
