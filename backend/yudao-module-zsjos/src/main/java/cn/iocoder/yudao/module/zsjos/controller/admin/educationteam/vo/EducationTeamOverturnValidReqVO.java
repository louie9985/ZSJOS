package cn.iocoder.yudao.module.zsjos.controller.admin.educationteam.vo;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class EducationTeamOverturnValidReqVO {
    @NotBlank @Size(max = 1000) private String reason;
    @NotBlank @Size(max = 100) private String idempotencyKey;
    @NotBlank @Size(max = 64) private String qualificationToken;
    @NotNull @Size(max = 9) private List<@Valid LeadAttachmentReqVO> attachments = new ArrayList<>();
}
