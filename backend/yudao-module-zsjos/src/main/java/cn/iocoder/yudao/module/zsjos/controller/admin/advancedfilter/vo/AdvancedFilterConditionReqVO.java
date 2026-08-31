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
    private Object value;
    private Object valueFrom;
    private Object valueTo;
}
