package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class DirectorStageSaveReqVO {
    private LocalDateTime interviewAt;
    @NotNull private Map<String, Object> data;
    @NotNull private Integer version;
    @NotBlank private String idempotencyKey;
}
