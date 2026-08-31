package cn.iocoder.yudao.module.eam.controller.admin.procurement.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EamReturnInspectReqVO {
    @NotNull(message = "验收结果不能为空")
    private Integer result;
    private String remark;
}
