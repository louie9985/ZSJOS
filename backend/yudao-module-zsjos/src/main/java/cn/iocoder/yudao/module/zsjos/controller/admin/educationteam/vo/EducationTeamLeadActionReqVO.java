package cn.iocoder.yudao.module.zsjos.controller.admin.educationteam.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EducationTeamLeadActionReqVO {
    @NotBlank
    @Size(max = 500)
    private String reason;
    @NotBlank
    @Size(max = 40)
    private String idempotencyKey;
    private Integer version;
    private Long targetUserId;
    private Long collaboratorUserId;
}
