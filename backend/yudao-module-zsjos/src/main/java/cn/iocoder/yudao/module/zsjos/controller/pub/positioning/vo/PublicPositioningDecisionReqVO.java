package cn.iocoder.yudao.module.zsjos.controller.pub.positioning.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicPositioningDecisionReqVO {
    @NotBlank
    @Pattern(regexp = "agree|request_changes")
    private String decision;
    @Size(max = 500)
    private String comment;
}
