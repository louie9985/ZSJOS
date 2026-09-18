package cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo;
import jakarta.validation.constraints.*; import lombok.Data;
@Data public class StudentDeliveryDeferReqVO { @NotNull private Long stageId; @NotNull private Long requestedBy; @Min(1) private Integer requestedDays; @NotNull private java.time.LocalDateTime newDueAt; @NotNull private Integer version; @NotBlank @Size(max=128) private String idempotencyKey; @NotBlank @Size(max=1000) private String reason; private Long supervisorUserId; }
