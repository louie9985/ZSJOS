package cn.iocoder.yudao.module.zsjos.controller.admin.content.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ContentVersionSaveReqVO {
    @NotNull private Long contentId;
    @NotBlank private String stage;
    private String materialRefsJson;
    private String deliverableUrl;
    private String scriptText;
    private String idempotencyKey;
}
