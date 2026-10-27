package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeliveryClassPageReqVO extends PageParam {
    @jakarta.validation.constraints.Pattern(regexp = "SELF|ALL|USER")
    private String readScope;
    private Long targetUserId;
    private String status;
    private String keyword;
    /** Optional ordering for grouped class pages and their children; flat pages retain their existing order. */
    @jakarta.validation.constraints.Pattern(regexp = "name|date|count")
    private String sortField;
    @jakarta.validation.constraints.Pattern(regexp = "asc|desc")
    private String sortOrder;

    @jakarta.validation.constraints.AssertTrue(message = "排序字段和方向必须同时提供")
    public boolean isSortValid() {
        return (sortField == null) == (sortOrder == null);
    }
    private Long categoryId;
    private Long examScheduleId;
    private Long homeroomUserId;
    @jakarta.validation.constraints.Positive
    private Long projectCategoryId;
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private java.time.LocalDate examDateStart;
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private java.time.LocalDate examDateEnd;
    private Boolean excludeSystemClass;
    private Boolean assignableOnly;
    /** Direct-transfer target scope; read-all grants do not expand this command projection. */
    private Boolean commandTargetsOnly;

    @jakarta.validation.constraints.AssertTrue(message = "结束日期不能早于开始日期")
    public boolean isExamDateRangeValid() {
        return examDateStart == null || examDateEnd == null || !examDateEnd.isBefore(examDateStart);
    }
}
