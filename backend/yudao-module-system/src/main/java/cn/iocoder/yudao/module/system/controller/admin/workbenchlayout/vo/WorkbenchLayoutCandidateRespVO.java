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
public class WorkbenchLayoutCandidateRespVO {

    @Builder.Default
    private List<Page> pages = Collections.emptyList();
    @Builder.Default
    private List<Role> roles = Collections.emptyList();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Page {
        private Long sourceMenuId;
        private String name;
        private String icon;
        private String path;
        private String workbenchRenderMode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Role {
        private Long id;
        private String name;
        private String code;
        private Integer status;
        private Integer publishedVersionNo;
        private Boolean publishedEnabled;
        private Integer publishedPriority;
    }

}
