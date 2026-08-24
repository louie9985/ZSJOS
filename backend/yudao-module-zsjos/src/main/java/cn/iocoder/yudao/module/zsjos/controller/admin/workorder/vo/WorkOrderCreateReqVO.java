package cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;
import java.util.Map;
@Data public class WorkOrderCreateReqVO {
    @NotBlank @Size(max = 64) private String sceneCode;
    private Long targetUserId;
    @NotNull private Map<String, Object> values;
    @Size(max = 20) private List<Long> attachmentIds;
    @NotBlank @Size(max = 128) private String idempotencyKey;
}
