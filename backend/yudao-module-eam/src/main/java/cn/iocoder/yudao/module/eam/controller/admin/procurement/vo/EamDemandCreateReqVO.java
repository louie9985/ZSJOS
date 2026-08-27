package cn.iocoder.yudao.module.eam.controller.admin.procurement.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class EamDemandCreateReqVO {
    private Long employeeId;
    private String reason;
    @Valid
    @NotEmpty(message = "需求明细不能为空")
    private List<EamDemandItemReqVO> items;
}
