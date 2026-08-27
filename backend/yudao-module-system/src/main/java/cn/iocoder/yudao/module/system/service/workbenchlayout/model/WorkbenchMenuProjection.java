package cn.iocoder.yudao.module.system.service.workbenchlayout.model;

import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO;
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
public class WorkbenchMenuProjection {

    @Builder.Default
    private List<AuthPermissionInfoRespVO.MenuVO> menus = Collections.emptyList();
    private Meta meta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private Long globalVersionId;
        private Integer globalVersionNo;
        @Builder.Default
        private List<AppliedRoleLayout> appliedRoleLayouts = Collections.emptyList();
        private Boolean fallback;
        private String fallbackReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppliedRoleLayout {
        private Long roleId;
        private Long versionId;
        private Integer versionNo;
        private Integer priority;
    }

}
