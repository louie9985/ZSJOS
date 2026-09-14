package cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull; import lombok.Data;
@Data public class StudentDeliverySubmissionReqVO {
 @NotNull private Long stageId; @NotNull private Long submittedBy; private Long templateVersionId;
 @NotBlank private String fieldValuesJson; private String dictionarySnapshotJson; private String attachmentSnapshotJson;
}
