package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class LeadSubmitterFeedbackReqVO {
    @NotBlank @Size(max = 5000) private String feedback;
    @Size(max = 20) private List<@NotNull @Positive Long> attachmentIds;
    @NotNull @Min(0) private Integer version;
    @NotBlank @Size(max = 128) private String idempotencyKey;
}

