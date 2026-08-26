package cn.iocoder.yudao.module.system.service.workbenchlayout.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkbenchLayoutSnapshot {

    public static final int SCHEMA_VERSION = 1;
    public static final String NODE_TYPE_GROUP = "GROUP";
    public static final String NODE_TYPE_PAGE = "PAGE";
    public static final String UNCLASSIFIED_KEY = "__unclassified__";
    public static final String UNCLASSIFIED_NAME = "未分类";

    @Builder.Default
    private Integer schemaVersion = SCHEMA_VERSION;
    private String scopeType;
    @Builder.Default
    private Boolean enabled = true;
    private Integer priority;
    @Builder.Default
    private List<Node> nodes = new ArrayList<>();
    @Builder.Default
    private List<Operation> operations = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Node {
        private String key;
        private String type;
        private Long sourceMenuId;
        private String name;
        private String icon;
        @Builder.Default
        private Boolean hidden = false;
        private Integer sort;
        @Builder.Default
        private List<Node> children = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Operation {
        private String key;
        private String type;
        private Long sourceMenuId;
        private String parentKey;
        private Integer sort;
        private Boolean hidden;
        private String name;
        private String icon;
    }

}
