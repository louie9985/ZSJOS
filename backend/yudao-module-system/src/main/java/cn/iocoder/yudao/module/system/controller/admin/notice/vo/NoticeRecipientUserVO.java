package cn.iocoder.yudao.module.system.controller.admin.notice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 公告接收用户选项 Response VO")
@Data
public class NoticeRecipientUserVO {

    @Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "7")
    private Long id;

    @Schema(description = "用户昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    private String nickname;

    @Schema(description = "所属部门编号", example = "1024")
    private Long deptId;

    @Schema(description = "是否可选", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean selectable;

    @Schema(description = "不可选原因")
    private String disabledReason;
}
