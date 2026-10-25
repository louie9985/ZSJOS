package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true)
public class StudentServicePageReqVO extends MyStudentPageReqVO {
    private Boolean pendingTasksFirst;
    private Long personId;
    @jakarta.validation.constraints.Positive private Long ownerUserId;
    @jakarta.validation.constraints.Pattern(regexp = "pending|accepted") private String acceptanceStatus;
    @jakarta.validation.constraints.Size(max = 64) private String deliveryStage;
    @jakarta.validation.constraints.Pattern(regexp = "none|pending|overdue") private String taskState;
}
