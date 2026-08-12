package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class SubordinateBatchPublicSeaReqVO extends SubordinateReasonReqVO {
    @NotEmpty(message = "至少选择一条客资")
    @Size(max = 200, message = "单次最多操作 200 条客资")
    private List<@NotNull Long> leadIds;
    private Long collaboratorUserId;
}
