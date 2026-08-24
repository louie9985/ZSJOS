package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StudentCollaboratorAssignReqVO {
    @NotBlank @Pattern(regexp = "content_director|career_planner") private String collaboratorType;
    @NotNull private Long userId;
    @NotNull private Integer version;
    @Size(max = 500) private String correctionReason;
    @NotBlank private String idempotencyKey;
}
