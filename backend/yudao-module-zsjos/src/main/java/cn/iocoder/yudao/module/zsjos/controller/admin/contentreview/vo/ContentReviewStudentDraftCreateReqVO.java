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

    /** Optimistic version for saving an existing draft; absent only for legacy clients. */
    private Integer expectedVersion;

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
        /** 参考作品链接，纯文本填写。 */
        @Size(max = 1024)
        private String referenceWorkUrl;
        /** 从素材库多选出的参考素材快照，随作品保存供审批查看。 */
        @Size(max = 20)
        private List<@Valid ReferenceMaterial> referenceMaterials;
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

    /** 素材库参考素材的选择结果，只保存进入审批所需的展示字段。 */
    @Data
    public static class ReferenceMaterial {
        @NotNull
        private Long materialId;
        private Long materialVersionId;
        @Size(max = 64)
        private String materialNo;
        @Size(max = 255)
        private String title;
        @Size(max = 128)
        private String materialTypeName;
        @Size(max = 1024)
        private String coverPreviewUrl;
    }
}
