package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PartnerConvertReqVO {
    @NotBlank private String targetType;
    @NotNull private Long deptId;
    private boolean migrateHistoricalOrganization;
    @NotBlank @Size(max = 500) private String reason;
}
