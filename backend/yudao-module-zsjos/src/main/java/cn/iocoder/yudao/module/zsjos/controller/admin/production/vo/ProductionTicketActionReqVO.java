package cn.iocoder.yudao.module.zsjos.controller.admin.production.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductionTicketActionReqVO {
    @NotNull private Integer version;
    @NotBlank @Size(max = 64) private String idempotencyKey;
    @Size(max = 500) private String reason;
    @Size(max = 1000) private String remark;
    @Size(max = 2000)
    @org.hibernate.validator.constraints.URL(protocol = "", regexp = "https?://[^\\s]+")
    private String completionUrl;
    private Long attachmentId;
    private Boolean videoSentToOperator;
}
