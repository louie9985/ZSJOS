package cn.iocoder.yudao.module.zsjos.controller.admin.production.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductionTicketSaveReqVO {
    @NotBlank @Size(max = 64) private String sceneCode;
    @NotNull private Long accountId;
    private Long assigneeUserId;
    private Long targetDeptId;
    @Size(max = 500) private String operatorRemark;
    private java.util.Map<String, Object> values;
    private java.util.List<Long> attachmentIds;
    @NotBlank @Size(max = 64) private String idempotencyKey;
}
