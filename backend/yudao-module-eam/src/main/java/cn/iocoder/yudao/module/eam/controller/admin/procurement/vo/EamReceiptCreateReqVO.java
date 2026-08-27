package cn.iocoder.yudao.module.eam.controller.admin.procurement.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class EamReceiptCreateReqVO {
    private String remark;
    private List<String> fileUrls;
    @Valid
    @NotEmpty(message = "入库明细不能为空")
    private List<EamReceiptItemReqVO> items;
}
