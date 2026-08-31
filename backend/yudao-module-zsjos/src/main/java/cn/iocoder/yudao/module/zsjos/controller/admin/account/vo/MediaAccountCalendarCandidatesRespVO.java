package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 媒体账号日历筛选候选人 Response VO")
@Data
public class MediaAccountCalendarCandidatesRespVO {

    @Schema(description = "编导候选人")
    private List<UserRespVO> directors;

    @Schema(description = "运营候选人")
    private List<UserRespVO> operators;

    @Schema(description = "用户简要信息")
    @Data
    public static class UserRespVO {
        @Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
        private Long id;

        @Schema(description = "用户昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
        private String nickname;

        @Schema(description = "用户账号", example = "zhangsan")
        private String username;

        @Schema(description = "用户状态", example = "0")
        private Integer status;

        @Schema(description = "部门编号", example = "100")
        private Long deptId;
    }
}
