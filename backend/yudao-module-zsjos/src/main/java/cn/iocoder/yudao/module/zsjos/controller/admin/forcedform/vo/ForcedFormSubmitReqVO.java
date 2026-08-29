package cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo;
import jakarta.validation.constraints.NotBlank; import lombok.Data;
@Data public class ForcedFormSubmitReqVO { @NotBlank private String answersJson; private String platform; }
