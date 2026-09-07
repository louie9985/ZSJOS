package cn.iocoder.yudao.module.system.controller.admin.notice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 公告接收范围选项 Response VO")
@Data
public class NoticeRecipientOptionsRespVO {

    @Schema(description = "启用的部门列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<NoticeRecipientDeptVO> departments;

    @Schema(description = "启用的用户列表；无公告阅读权限的用户不可选", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<NoticeRecipientUserVO> users;
}
