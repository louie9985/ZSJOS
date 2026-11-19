package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class SalesOrderNativeActionReqVO {

    @NotBlank
    private String taskId;
    @NotBlank
    private String action;
    @Size(max = 1000)
    private String reason;
    private String type;
    private Set<@Positive Long> userIds;
}
