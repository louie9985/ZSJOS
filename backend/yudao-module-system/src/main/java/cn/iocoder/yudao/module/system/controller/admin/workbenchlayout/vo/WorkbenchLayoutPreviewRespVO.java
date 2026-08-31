package cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo;

import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO;
import cn.iocoder.yudao.module.system.service.workbenchlayout.model.WorkbenchMenuProjection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkbenchLayoutPreviewRespVO {

    private Long userId;
    private String userName;
    @Builder.Default
    private Set<Long> roleIds = Collections.emptySet();
    @Builder.Default
    private Set<String> permissions = Collections.emptySet();
    @Builder.Default
    private List<AuthPermissionInfoRespVO.MenuVO> finalTree = Collections.emptyList();
    private WorkbenchMenuProjection.Meta meta;
    @Builder.Default
    private List<FilteredItem> filteredItems = Collections.emptyList();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilteredItem {
        private Long sourceMenuId;
        private String name;
        private String reason;
    }

}
