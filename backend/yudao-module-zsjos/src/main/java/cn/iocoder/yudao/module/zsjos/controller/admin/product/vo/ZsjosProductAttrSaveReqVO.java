package cn.iocoder.yudao.module.zsjos.controller.admin.product.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ZsjosProductAttrSaveReqVO {
    @NotNull private Long spuId;
    @Valid private List<Attr> attrs = List.of();

    @Data
    public static class Attr {
        @Size(max = 64) private String attrKey;
        @NotBlank @Size(max = 50) private String attrName;
        @NotNull private Boolean required;
        @NotNull private Integer sort;
        @NotEmpty @Valid private List<Value> values;
    }
    @Data
    public static class Value {
        @NotBlank @Size(max = 100) private String value;
        @NotBlank @Size(max = 100) private String label;
        @NotNull private Integer sort;
    }
}
