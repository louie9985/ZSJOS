package cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo;
import jakarta.validation.constraints.*; import lombok.Data;
@Data public class StudentDeliveryDeferReqVO { @NotNull private Long stageId; @NotNull private Long requestedBy; @NotNull @Min(1) private Integer requestedDays; @NotBlank @Size(max=1000) private String reason; private Long supervisorUserId; }
