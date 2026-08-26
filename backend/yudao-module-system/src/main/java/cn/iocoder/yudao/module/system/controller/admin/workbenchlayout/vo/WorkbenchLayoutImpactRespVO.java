package cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkbenchLayoutImpactRespVO {

    private Boolean publishable;
    private Integer affectedRoleCount;
    @Builder.Default
    private List<Issue> issues = Collections.emptyList();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Issue {
        private Long roleId;
        private String roleName;
        private String message;
    }

}
