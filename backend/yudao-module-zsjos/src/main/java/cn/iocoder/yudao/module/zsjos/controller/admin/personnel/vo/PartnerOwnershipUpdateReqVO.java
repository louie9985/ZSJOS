package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PartnerOwnershipUpdateReqVO {
    private Long assignedUserId;
    private Integer expectedVersion;
    @NotBlank(message = "调整原因不能为空")
    @Size(max = 500, message = "调整原因不能超过 500 字")
    private String reason;
}
