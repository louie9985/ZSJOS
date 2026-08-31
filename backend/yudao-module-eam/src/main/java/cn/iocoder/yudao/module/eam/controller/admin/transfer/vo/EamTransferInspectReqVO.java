package cn.iocoder.yudao.module.eam.controller.admin.transfer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "管理后台 - EAM 资产退还/归还验收 Request VO")
public class EamTransferInspectReqVO {

    @NotNull(message = "验收结果不能为空")
    @Schema(description = "验收结果：1 完好 2 损坏 3 缺件或遗失 4 不符驳回", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer result;

    @Size(max = 500, message = "验收备注不能超过 500 个字符")
    private String remark;

    @Size(max = 20, message = "验收附件不能超过 20 个")
    private List<String> fileUrls;
}
