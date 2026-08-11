package cn.iocoder.yudao.module.system.controller.admin.notify.vo.rule;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 业务通知规则创建/修改 Request VO")
@Data
public class NotifyRuleSaveReqVO {

    private Long id;

    @NotEmpty(message = "规则名称不能为空")
    @Size(max = 64, message = "规则名称不能超过 64 个字符")
    private String name;

    @NotEmpty(message = "通知场景不能为空")
    private String sceneCode;

    @NotEmpty(message = "通知渠道不能为空")
    private String channelCode;

    @NotNull(message = "通知模板不能为空")
    private Long templateId;

    private List<String> recipientRoles;
    private List<Long> specifiedUserIds;

    @NotEmpty(message = "点击动作不能为空")
    private String actionType;

    private String timingStage;
    private Integer timingOffsetMinutes;

    @NotNull(message = "状态不能为空")
    @InEnum(value = CommonStatusEnum.class, message = "状态必须是 {value}")
    private Integer status;
}
