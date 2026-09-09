package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentVersionFileRespVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ContentReviewBatchItemRespVO {
    private Long id;
    private Long contentId;
    private Long contentVersionId;
    private Integer contentRecordVersion;
    private Integer sortNo;
    private Map<String, Object> contentSnapshot;
    private List<ContentVersionFileRespVO> files;
    private String directorDecision;
    private String directorComment;
    private Long directorReviewedByUserId;
    private LocalDateTime directorReviewedAt;
    private String finalDecision;
    private String finalComment;
    private Boolean collectMaterial;
    private Long finalReviewedByUserId;
    private LocalDateTime finalReviewedAt;
    private Long collectedMaterialId;
    private Long collectedMaterialVersionId;
    private String resultStatus;
    private String publishedPlatformUrl;
    private LocalDateTime publishedAt;
    private Long publishedByUserId;
    private Integer version;
}
