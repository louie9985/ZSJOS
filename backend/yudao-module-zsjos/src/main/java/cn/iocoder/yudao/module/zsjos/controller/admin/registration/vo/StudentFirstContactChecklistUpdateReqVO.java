package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class StudentFirstContactChecklistUpdateReqVO {
    @NotNull private Integer version;
    @NotNull private List<String> completedChecklistKeys;
    private List<Long> attachmentFileIds;
    @NotBlank @Size(max = 64) private String idempotencyKey;
}
