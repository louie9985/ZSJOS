package cn.iocoder.yudao.module.zsjos.controller.admin.content.vo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data public class ContentSaveReqVO {
    @NotNull private Long accountId; @NotBlank @Size(max = 255) private String title;
    @Size(max = 1000) private String topic;
    @NotBlank private String contentClassValue;
    @NotBlank private String contentClassLabelSnapshot;
}
