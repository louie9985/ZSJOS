package cn.iocoder.yudao.module.system.controller.admin.notify.vo.message;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "管理后台 - 站内信 Response VO")
@Data
public class NotifyMessageRespVO {

    @Schema(description = "ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "25025")
    private Long userId;

    @Schema(description = "用户类型，参见 UserTypeEnum 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Byte userType;

    @Schema(description = "模版编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13013")
    private Long templateId;

    @Schema(description = "模板编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "test_01")
    private String templateCode;

    @Schema(description = "模版发送人名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋艿")
    private String templateNickname;

    @Schema(description = "消息标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateTitle;

    @Schema(description = "消息摘要", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateSummary;

    @Schema(description = "模版内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "测试内容")
    private String templateContent;

    @Schema(description = "模版类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Integer templateType;

    @Schema(description = "模版参数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<String, Object> templateParams;

    @Schema(description = "通知规则编号")
    private Long notifyRuleId;

    @Schema(description = "业务通知场景编码")
    private String sceneCode;

    @Schema(description = "受控点击动作：none、message_detail、business_detail")
    private String actionType;

    @Schema(description = "业务类型")
    private String bizType;

    @Schema(description = "业务编号")
    private Long bizId;

    @Schema(description = "是否已读", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean readStatus;

    @Schema(description = "阅读时间")
    private LocalDateTime readTime;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
