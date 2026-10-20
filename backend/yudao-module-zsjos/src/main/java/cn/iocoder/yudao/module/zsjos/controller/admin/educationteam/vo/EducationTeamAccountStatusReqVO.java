package cn.iocoder.yudao.module.zsjos.controller.admin.educationteam.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EducationTeamAccountStatusReqVO extends EducationTeamReasonReqVO {
    @NotNull(message = "账号状态不能为空")
    private Integer status;
}
