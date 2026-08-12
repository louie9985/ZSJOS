package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class WorkTaskSaveReqVO {
    private Long id;
    private Long parentTaskId;
    @NotBlank @Size(max = 200) private String title;
    @Size(max = 4000) private String description;
    @Size(max = 2000) private String deliverableRequirement;
    @NotNull private Long assigneeUserId;
    private LocalDateTime dueAt;
    private LocalDateTime remindAt;
    @NotNull private Boolean confirmationRequired;
    private Long confirmerUserId;
    private Map<String, Object> taskFields;
    private Integer version;
    private String reason;

    @AssertTrue(message = "需要确认时必须选择确认人")
    public boolean isConfirmationValid() {
        return !Boolean.TRUE.equals(confirmationRequired) || confirmerUserId != null;
    }
}
