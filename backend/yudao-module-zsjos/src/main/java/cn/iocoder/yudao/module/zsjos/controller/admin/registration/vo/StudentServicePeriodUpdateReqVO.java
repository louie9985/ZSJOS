package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StudentServicePeriodUpdateReqVO {
    @NotNull
    private Boolean inServicePeriod;
}
