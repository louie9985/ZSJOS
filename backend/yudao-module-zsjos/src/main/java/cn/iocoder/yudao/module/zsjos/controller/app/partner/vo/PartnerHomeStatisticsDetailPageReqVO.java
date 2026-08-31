package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PartnerHomeStatisticsDetailPageReqVO extends PageParam {
    @NotBlank
    private String period;
    @NotBlank
    private String metric;
}
