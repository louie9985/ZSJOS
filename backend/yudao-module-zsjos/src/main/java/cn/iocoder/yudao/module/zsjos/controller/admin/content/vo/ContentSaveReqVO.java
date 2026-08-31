package cn.iocoder.yudao.module.zsjos.controller.admin.content.vo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Data public class ContentSaveReqVO {
    @NotNull private Long accountId; @NotBlank private String title;
    private String topic; @NotBlank private String contentClassValue; @NotBlank private String contentClassLabelSnapshot;
}
