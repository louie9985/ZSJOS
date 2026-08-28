package cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data public class WorkOrderActionReqVO {
    @NotNull private Integer version;
    @Size(max = 1000) private String reason;
    @Size(max = 4000) private String resultRemark;
    @Size(max = 20) private java.util.List<Long> attachmentIds;
    @NotBlank @Size(max = 128) private String idempotencyKey;
}
