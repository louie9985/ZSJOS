package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RegistrationRoutesUpdateReqVO {
    @NotNull private Integer version;
    @NotBlankIdempotency private String idempotencyKey;
    @NotEmpty @Size(max = 20) private List<@Valid RouteReqVO> routes;

    @Data
    public static class RouteReqVO {
        @NotNull private Long routeId;
        @NotNull private Boolean selected;
        private Long assigneeUserId;
    }
}
