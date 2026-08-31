package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeadTransferReqVO extends LeadDispositionReqVO {
    @NotNull
    private Long salesUserId;
}
