package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SalesOrderNativeCommentReqVO {
    @NotBlank
    private String taskId;
    @NotBlank
    @Size(max = 500)
    private String message;
}
