package cn.iocoder.yudao.module.zsjos.controller.admin.pointsredemption.vo;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class PointsPackageSaveReqVO {
    private Long id;
    @NotBlank @Size(max=100) private String name;
    @NotNull @Min(1) @Max(1000000) private Integer creditsAmount;
    @Min(1) @Max(36500) private Integer validDays;
    @NotNull private Boolean enabled;
}
