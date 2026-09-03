package cn.iocoder.yudao.module.system.controller.admin.notify.vo.channel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 通知渠道配置")
@Data
public class NotifyChannelConfigRespVO {
    private String channelCode;
    private Boolean enabled;
    private String configRef;
    private String maskedConfig;
    private Boolean socialClientConfigured;
}
