package cn.iocoder.yudao.module.eam.controller.admin.asset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - EAM 参考台账预检/提交结果")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EamAssetImportPreviewRespVO {

    private String fileHash;
    private Integer totalRows;
    private Integer createCount;
    private Integer updateCount;
    private Integer skipCount;
    private Integer warningCount;
    private Long batchId;
    @Builder.Default
    private List<Row> rows = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private Integer rowNum;
        private String assetCode;
        private String name;
        private String categoryName;
        private Integer managementMode;
        private Integer quantity;
        private String sourceUserName;
        private String matchedUserName;
        private String action;
        @Builder.Default
        private Map<String, Object> sourceFields = new LinkedHashMap<>();
        @Builder.Default
        private List<String> defaultedFields = new ArrayList<>();
        @Builder.Default
        private List<String> warnings = new ArrayList<>();
    }

}
