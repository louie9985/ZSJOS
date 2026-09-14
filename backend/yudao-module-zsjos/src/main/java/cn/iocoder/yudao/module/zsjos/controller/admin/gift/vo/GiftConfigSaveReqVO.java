package cn.iocoder.yudao.module.zsjos.controller.admin.gift.vo; import jakarta.validation.constraints.*; import lombok.Data;
@Data public class GiftConfigSaveReqVO { private Long id; private Long parentId; @NotBlank private String name; @NotBlank private String code; private Integer status=1; private Integer sort=0; }
