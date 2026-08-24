package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RegistrationChecklistDraftSaveReqVO {
    @NotNull private Integer templateVersion;
    @NotBlankIdempotency private String idempotencyKey;
    @NotEmpty @Size(max = 50) private List<@Valid ItemReqVO> items;
    @NotEmpty @Size(max = 20) private List<@Valid RouteOptionReqVO> routeOptions;

    @Data
    public static class ItemReqVO {
        private Long id;
        @Size(max = 64) private String itemKey;
        @Size(max = 32) private String itemType;
        @NotBlank @Size(max = 100) private String title;
        @NotNull @Max(9999) private Integer sort;
        @NotNull private Boolean enabled;
        private Boolean systemRequired;
        private Boolean attachmentRequired;
    }

    @Data
    public static class RouteOptionReqVO {
        private Long id;
        @NotBlank @Size(max = 64) private String optionKey;
        @NotNull private Long departmentId;
        @NotBlank @Size(max = 32) private String assigneeType;
        @NotNull @Max(9999) private Integer sort;
        @NotNull private Boolean enabled;
        private Boolean systemRequired;
    }
}
