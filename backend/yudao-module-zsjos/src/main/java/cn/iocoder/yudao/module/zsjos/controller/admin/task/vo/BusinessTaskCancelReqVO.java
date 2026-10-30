package cn.iocoder.yudao.module.zsjos.controller.admin.task.vo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data
public class BusinessTaskCancelReqVO {
    @NotBlank(message = "请填写取消原因")
    @Size(max = 500, message = "取消原因最多500字")
    private String reason;
}
