package cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull; import lombok.Data; import java.util.List;
@Data public class ForcedFormSaveReqVO { private Long id; @NotBlank private String name; private String description; @NotBlank private String fieldsJson; private String status; private List<Long> userIds; private String scope; }
