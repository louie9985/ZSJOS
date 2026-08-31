package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
public class LeadAssignmentSaveReqVO {

    @NotEmpty(message = "派单员工不能为空")
    private Set<Long> sourceUserIds;
    private Set<Long> targetUserIds;
    @NotBlank(message = "操作模式不能为空")
    private String mode;

}
