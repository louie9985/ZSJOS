package cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class PositioningLinkRespVO {
    private String sharePath;
    private LocalDateTime expiresAt;
}
