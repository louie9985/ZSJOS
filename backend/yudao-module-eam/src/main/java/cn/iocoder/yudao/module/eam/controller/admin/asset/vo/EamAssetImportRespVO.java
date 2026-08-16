package cn.iocoder.yudao.module.eam.controller.admin.asset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - EAM 资产导入结果 Response VO")
@Data
@Builder
public class EamAssetImportRespVO {

    @Schema(description = "创建成功的资产编号列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @Builder.Default
    private List<String> createAssetCodes = new ArrayList<>();

    @Schema(description = "导入失败的行及原因，key 为行号", requiredMode = Schema.RequiredMode.REQUIRED)
    @Builder.Default
    private List<FailureItem> failures = new ArrayList<>();

    @Schema(description = "导入失败项")
    @Data
    @Builder
    public static class FailureItem {

        @Schema(description = "Excel 行号（含表头，从 2 开始）", example = "3")
        private Integer rowNum;

        @Schema(description = "资产名称", example = "MacBook Pro")
        private String name;

        @Schema(description = "失败原因", example = "分类编码【IT】不存在")
        private String reason;

    }

}
