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
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
