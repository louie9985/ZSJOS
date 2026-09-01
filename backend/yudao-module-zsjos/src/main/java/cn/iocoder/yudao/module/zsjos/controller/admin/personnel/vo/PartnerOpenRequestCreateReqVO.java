package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import cn.iocoder.yudao.framework.common.validation.Mobile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PartnerOpenRequestCreateReqVO {

    @NotBlank
    @Size(max = 100)
    private String partnerName;

    @NotBlank
    @Mobile
    private String partnerMobile;

    @NotNull
    private Long assignedEmployeeUserId;

    @Size(max = 128)
    private String idempotencyKey;
}
