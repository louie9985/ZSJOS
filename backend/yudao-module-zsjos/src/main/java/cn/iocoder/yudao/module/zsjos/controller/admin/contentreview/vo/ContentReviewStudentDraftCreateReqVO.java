package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

/** Request used by the student overview to create a content-review draft. */
@Data
public class ContentReviewStudentDraftCreateReqVO {

    @NotNull
    private Long studentPersonId;

    @NotEmpty
    @Size(max = 20)
    private List<@NotNull Long> accountIds;

    /** Editable account profile values supplied by the operator; persisted only in the batch snapshot. */
    private Map<String, Map<String, Object>> accountSnapshots;

    @NotEmpty
    @Size(max = 20)
    private List<@Valid Work> works;

    @Data
    public static class Work {
        private Long sourceContentId;
        private Long sourceVersionId;
        @NotBlank
        @Size(max = 255)
        private String title;
        @Size(max = 1000)
        private String topic;
        /** Existing content classification dictionary; daily is used when omitted by the new editor. */
        @Size(max = 64)
        private String contentClassValue;
        @Size(max = 255)
        private String contentClassLabelSnapshot;
        private String purposeValue;
        private String purposeLabelSnapshot;
        private String formatValue;
        private String formatLabelSnapshot;
        private String coverSnapshotJson;
        private Long coverFileId;
        private String materialRefsJson;
        private Long referenceContentVersionId;
        private String deliverableUrl;
        private String deliverableSnapshotJson;
        private String scriptText;
        private String detailUrl;
        private String commentHook;
        private String leadResourceUrl;
        private LocalDateTime plannedPublishAt;
        @Size(max = 128)
        private String idempotencyKey;
    }
}
