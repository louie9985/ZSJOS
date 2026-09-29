package cn.iocoder.yudao.module.system.controller.admin.notify.vo.message;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理后台 - 我的站内信分类 Response VO")
public record NotifyMessageCategoryRespVO(

        @Schema(description = "分类编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "lead")
        String key,

        @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "客资")
        String label

) {
}
