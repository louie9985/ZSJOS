package cn.iocoder.yudao.module.zsjos.controller.admin.pointsredemption.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PointsUrgeReqVO {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{16,80}")
    private String idempotencyKey;
}
