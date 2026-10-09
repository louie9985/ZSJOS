package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubordinateNewcomerPoolReqVO {
    @NotNull private Boolean enabled;
    @NotNull @Min(1) private Integer dailyQuota;
}
