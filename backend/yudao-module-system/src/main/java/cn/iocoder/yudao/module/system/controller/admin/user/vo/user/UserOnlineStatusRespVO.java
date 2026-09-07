package cn.iocoder.yudao.module.system.controller.admin.user.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Schema(description = "管理后台 - 用户在线状态 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserOnlineStatusRespVO {

    @Schema(description = "在线状态服务是否可用", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean available;

    @Schema(description = "请求用户中当前在线的用户编号")
    private Set<Long> onlineUserIds;

    @Schema(description = "当前租户启用员工的在线人数")
    private Long onlineCount;

    @Schema(description = "状态观测时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime observedAt;

}
