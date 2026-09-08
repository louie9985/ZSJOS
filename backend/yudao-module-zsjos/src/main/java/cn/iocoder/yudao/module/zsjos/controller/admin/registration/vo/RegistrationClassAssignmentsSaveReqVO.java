package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RegistrationClassAssignmentsSaveReqVO {
    @NotNull private Integer version;
    @NotBlank @Size(max = 64) private String idempotencyKey;
    @Valid @NotEmpty private List<AssignmentReqVO> assignments;

    @Data
    public static class AssignmentReqVO {
        @NotNull private Long orderItemId;
        @NotNull private Long classId;
    }
}
