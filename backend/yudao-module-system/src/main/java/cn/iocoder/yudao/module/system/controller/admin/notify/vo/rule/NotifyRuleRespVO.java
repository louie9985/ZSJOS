package cn.iocoder.yudao.module.system.controller.admin.notify.vo.rule;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 业务通知规则 Response VO")
@Data
public class NotifyRuleRespVO {

    private Long id;
    private String name;
    private String sceneCode;
    private String channelCode;
    private Long templateId;
    private List<String> recipientRoles;
    private List<Long> specifiedUserIds;
    private String actionType;
    private String timingStage;
    private Integer timingOffsetMinutes;
    private Integer status;
    private LocalDateTime createTime;
}
