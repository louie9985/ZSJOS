package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MaterialRespVO {
    private Long id;
    private String materialNo;
    private Long materialTypeId;
    private String materialTypeName;
    private String title;
    private Long coverFileId;
    private String coverPreviewUrl;
    private String summary;
    private String source;
    private String status;
    private Long currentEffectiveVersionId;
    private Long currentDraftVersionId;
    private Long ownerUserId;
    private String ownerName;
    private Long likeCount;
    private Long favoriteCount;
    private Long referenceCount;
    private Boolean liked;
    private Boolean favorited;
    private Boolean pinned;
    private Integer priority;
    private String disabledReason;
    private LocalDateTime disabledAt;
    private Integer version;
    private MaterialVersionRespVO currentVersion;
    private List<String> availableActions;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
