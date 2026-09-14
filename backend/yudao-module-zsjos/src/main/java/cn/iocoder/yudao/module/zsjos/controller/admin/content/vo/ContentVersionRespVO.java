package cn.iocoder.yudao.module.zsjos.controller.admin.content.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ContentVersionRespVO {
    private Long id;
    private Long contentId;
    private Integer versionNo;
    private String stage;
    private String titleSnapshot;
    private String topicSnapshot;
    private String coverSnapshotJson;
    private String materialRefsJson;
    private String deliverableUrl;
    private String deliverableSnapshotJson;
    private String scriptText;
    private String purposeValue;
    private String purposeLabelSnapshot;
    private String formatValue;
    private String formatLabelSnapshot;
    private String detailUrl;
    private String commentHook;
    private String leadResourceUrl;
    private LocalDateTime plannedPublishAt;
    private LocalDateTime frozenAt;
    private Long submittedByUserId;
    private LocalDateTime submittedAt;
    private String reviewDecision;
    private String reviewComment;
    private Long reviewedByUserId;
    private LocalDateTime reviewedAt;
    private List<ContentVersionFileRespVO> files;
}

