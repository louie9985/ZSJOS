package cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo;
import jakarta.validation.constraints.*;
public record PositioningApplyReqVO(@NotNull Long accountId, @NotNull Long submissionId,
        @NotNull @Min(0) Integer version, @NotBlank @Size(max=64) String idempotencyKey) {}
