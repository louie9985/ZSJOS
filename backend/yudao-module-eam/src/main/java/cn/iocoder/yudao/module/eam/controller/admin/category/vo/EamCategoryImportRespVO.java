package cn.iocoder.yudao.module.eam.controller.admin.category.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - EAM 分类配置导入预检/提交结果")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EamCategoryImportRespVO {

    private Integer createCount;
    private Integer updateCount;
    private Integer skipCount;
    private Integer conflictCount;
    private Integer categoryCount;
    private Integer leafCategoryCount;
    private Integer fieldCount;
    private Integer legacyFieldCount;
    private Integer credentialFieldCount;
    private Boolean allManagementFieldsOptional;
    @Builder.Default
    private List<Item> items = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String kind;
        private String code;
        private String name;
        private String action;
        private String message;
    }

}
