package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class DeliveryClassBatchCreateReqVO {
    @NotBlank @Size(max = 100) private String className;
    private LocalDate startDate;
    @NotEmpty private List<@NotNull Long> homeroomUserIds;
}
