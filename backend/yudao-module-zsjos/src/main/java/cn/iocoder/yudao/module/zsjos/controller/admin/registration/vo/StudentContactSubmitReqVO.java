package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class StudentContactSubmitReqVO {
    @NotNull private Long taskId;
    @NotNull private Boolean successful;
    private String unsuccessfulReasonValue;
    @NotBlank @Size(max = 2000) private String remark;
    @Future @NotNull private LocalDateTime nextContactAt;
    private List<Long> attachmentFileIds;
    private Map<String, Object> data;
    @NotBlank private String idempotencyKey;
}
