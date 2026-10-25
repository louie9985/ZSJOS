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
    private Long examScheduleId;
    private java.time.LocalDate startDate;
    private java.time.LocalDate endDate;
    /** Existing clients omit this field; the new editor sets it when only metadata changes. */
    private Boolean preserveExamDate;

    @jakarta.validation.constraints.AssertTrue(message = "结束日期必须有开始日期，且不能早于开始日期")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isExamDateValid() {
        if (startDate == null) return endDate == null;
        return endDate == null || !endDate.isBefore(startDate);
    }
    @NotNull private Long homeroomUserId;
    private Integer version;
}
