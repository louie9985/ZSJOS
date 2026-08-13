package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PersonnelStateUpdateReqVO {
    @NotBlank private String state;
    @NotBlank @Size(max = 500) private String reason;
}
