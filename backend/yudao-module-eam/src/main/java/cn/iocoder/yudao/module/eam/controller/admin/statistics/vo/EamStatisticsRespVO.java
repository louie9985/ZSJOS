package cn.iocoder.yudao.module.eam.controller.admin.statistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - EAM 资产统计概览 Response VO")
@Data
public class EamStatisticsRespVO {

    @Schema(description = "资产总数", requiredMode = Schema.RequiredMode.REQUIRED, example = "320")
    private Long totalCount;

    @Schema(description = "按状态分布")
    private List<Item> statusStats;

    @Schema(description = "按分类分布")
    private List<Item> categoryStats;

    @Schema(description = "按使用部门分布")
    private List<Item> deptStats;

    @Schema(description = "统计项")
    @Data
    public static class Item {

        @Schema(description = "维度取值（状态码 / 分类ID / 部门ID）", example = "1")
        private String key;

        @Schema(description = "维度名称", example = "在用")
        private String name;

        @Schema(description = "数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "120")
        private Long count;

    }

}
