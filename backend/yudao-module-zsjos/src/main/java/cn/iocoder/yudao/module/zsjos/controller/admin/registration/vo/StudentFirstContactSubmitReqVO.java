package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class StudentFirstContactSubmitReqVO {
    @NotNull private Long taskId;
    @NotNull private Boolean successful;
    private String unsuccessfulReasonValue;
    @NotBlank @Size(max = 2000) private String remark;
    @Future @NotNull private LocalDateTime nextContactAt;
    private List<Long> attachmentFileIds;
    private List<String> completedChecklistKeys;
    private String extensionReasonValue;
    @Size(max = 1000) private String extensionDescription;
    private List<Long> extensionAttachmentFileIds;
    @NotBlank private String idempotencyKey;
}
