package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LeadProductReqVO {
    private String productRef;
    private String spuRef;
    private String skuRef;
    @NotNull private Boolean spuUnknown = false;
    @NotNull private Boolean skuUnknown = false;
    @NotNull(message = "必须标记是否主意向课程")
    private Boolean primary;

    public String effectiveSpuRef() {
        return spuRef != null ? spuRef : productRef;
    }
}
