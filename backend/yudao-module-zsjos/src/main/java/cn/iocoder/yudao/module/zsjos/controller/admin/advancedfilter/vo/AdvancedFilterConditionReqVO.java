package cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdvancedFilterConditionReqVO {
    @NotBlank private String fieldKey;
    @NotBlank private String operator;
    private String startFieldKey;
    private String endFieldKey;
    private String unit;
    /** Frozen business date for metric query contexts; absent on ordinary live filters. */
    private java.time.LocalDate relativeBaseDate;
    private Object value;
    private Object valueFrom;
    private Object valueTo;
}
