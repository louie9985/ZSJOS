package cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdvancedFilterTemplateRespVO {
    private Long id;
    private String scene;
    private String pageKey;
    private String scope;
    private String name;
    private AdvancedFilterGroupReqVO filter;
    private Integer sort;
    private Boolean enabled;
    private Boolean defaultTemplate;
    /**
     * 页面自动套用时会选中的那一条，仅 visible-list 返回时计算。
     * 同一页面可能同时存在个人默认与系统默认，优先级由服务端唯一决定，前端不重复实现。
     */
    private Boolean effectiveDefault;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
