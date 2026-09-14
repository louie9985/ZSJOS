package cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo;
import jakarta.validation.constraints.*; import lombok.Data;
@Data public class StudentDeliveryConfigReqVO { @NotNull @Min(0) @Max(365) private Integer s0Days; @NotNull @Min(0) @Max(365) private Integer s1Days; @NotNull @Min(0) @Max(365) private Integer s2Days; @NotNull @Min(0) @Max(365) private Integer s3Days; @NotNull @Min(0) @Max(365) private Integer s4Days; @NotNull @Min(0) @Max(365) private Integer s5Days; @NotNull @Min(0) @Max(365) private Integer s6Days; }
