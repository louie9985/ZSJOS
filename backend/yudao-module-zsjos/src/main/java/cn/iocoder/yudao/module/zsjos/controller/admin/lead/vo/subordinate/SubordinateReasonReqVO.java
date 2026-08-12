package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubordinateReasonReqVO {
    @NotBlank(message = "操作原因不能为空")
    @Size(max = 500, message = "操作原因不能超过 500 个字符")
    private String reason;
}
