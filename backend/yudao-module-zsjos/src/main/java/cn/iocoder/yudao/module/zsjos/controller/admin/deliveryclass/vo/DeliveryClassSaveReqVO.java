package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Map;
import java.util.Set;

@Data
public class DeliveryClassSaveReqVO {
    @jakarta.validation.constraints.NotBlank @Size(max = 100) private String className;
    private Long productId;
    private Map<String, String> selectedAttrs;
    private Set<Long> selectedSkuIds;
    private Long categoryId;
    @NotNull private Long examScheduleId;
    @NotNull private Long homeroomUserId;
    private Integer version;
}
