package cn.iocoder.yudao.module.eam.controller.admin.employeeasset.vo;

import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamDemandCreateReqVO;
import jakarta.validation.Valid;
import lombok.Data;

@Data
public class EamEmployeeAssetTaskSubmitReqVO {
    @Valid
    private EamDemandCreateReqVO demand;
    private String remark;
}
