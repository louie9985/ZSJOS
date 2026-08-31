package cn.iocoder.yudao.module.system.controller.admin.notify.vo.template;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "管理后台 - 站内信模版创建/修改 Request VO")
@Data
public class NotifyTemplateSaveReqVO {

    @Schema(description = "ID", example = "1024")
    private Long id;

    @Schema(description = "模版名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "测试模版")
    @NotEmpty(message = "模版名称不能为空")
    private String name;

    @Schema(description = "模版编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "SEND_TEST")
    @NotNull(message = "模版编码不能为空")
    private String code;

    @Schema(description = "模版类型，对应 system_notify_template_type 字典", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "模版类型不能为空")
    private Integer type;

    @Schema(description = "发送人名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "土豆")
    @NotEmpty(message = "发送人名称不能为空")
    private String nickname;

    @Schema(description = "业务通知场景编码")
    private String sceneCode;

    @Schema(description = "通知渠道编码")
    private String channelCode;

    private String smsTemplateId;
    private String wecomMessageType;

    @Schema(description = "通知标题模板", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "通知标题不能为空")
    @Size(max = 128, message = "通知标题不能超过 128 个字符")
    private String title;

    @Schema(description = "通知摘要模板", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "通知摘要不能为空")
    @Size(max = 512, message = "通知摘要不能超过 512 个字符")
    private String summary;

    @Schema(description = "模版内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "我是模版内容")
    @NotEmpty(message = "模版内容不能为空")
    private String content;

    @Schema(description = "状态，参见 CommonStatusEnum 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "状态不能为空")
    @InEnum(value = CommonStatusEnum.class, message = "状态必须是 {value}")
    private Integer status;

    @Schema(description = "备注", example = "我是备注")
    private String remark;

}
