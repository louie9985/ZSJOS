package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContentReviewPublishReqVO {
    @NotBlank @Size(max = 1024) private String platformUrl;
    @NotNull private LocalDateTime publishedAt;
    @NotNull private Integer expectedContentVersion;
}
