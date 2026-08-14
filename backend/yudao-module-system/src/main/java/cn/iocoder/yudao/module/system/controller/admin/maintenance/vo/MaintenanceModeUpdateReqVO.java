package cn.iocoder.yudao.module.system.controller.admin.maintenance.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MaintenanceModeUpdateReqVO {
    @NotNull private Boolean enabled;
}
