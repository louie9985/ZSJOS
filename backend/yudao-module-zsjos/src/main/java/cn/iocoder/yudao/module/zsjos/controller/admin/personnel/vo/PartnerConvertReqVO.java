package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PartnerConvertReqVO {
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$") private String username;
    @NotBlank @Size(min = 8, max = 20)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$") private String password;
    @NotBlank private String targetType;
    @NotNull private Long deptId;
    private boolean migrateHistoricalOrganization;
    @NotBlank @Size(max = 500) private String reason;
}
