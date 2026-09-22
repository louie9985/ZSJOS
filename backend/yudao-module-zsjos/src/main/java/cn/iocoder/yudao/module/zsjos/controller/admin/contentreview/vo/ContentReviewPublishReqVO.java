package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContentReviewPublishReqVO {
    @NotBlank(message = "请填写平台发布链接") @Size(max = 1024, message = "平台链接不能超过 1024 字") private String platformUrl;
    @NotNull(message = "请选择发布时间") private LocalDateTime publishedAt;
    @NotNull(message = "缺少内容版本，请刷新后重试") private Integer expectedContentVersion;
}
