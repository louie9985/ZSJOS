package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeliveryClassDirectTransferReqVO {
    @NotNull private Long targetClassId;
    @NotNull private Integer version;
    @NotBlank @Size(max = 500) private String reason;
}
