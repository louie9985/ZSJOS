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

    @NotNull(message = "请选择学员")
    private Long studentPersonId;

    @NotEmpty(message = "请选择发布账号")
    @Size(max = 20, message = "发布账号最多选择 20 个")
    private List<@NotNull(message = "发布账号无效") Long> accountIds;

    /** Editable account profile values supplied by the operator; persisted only in the batch snapshot. */
    private Map<String, Map<String, Object>> accountSnapshots;

    @NotEmpty(message = "请至少添加一件作品")
    @Size(max = 20, message = "每批次最多包含 20 件作品")
    private List<@NotNull(message = "作品不能为空") @Valid Work> works;

    @Data
    public static class Work {
        private Long sourceContentId;
        private Long sourceVersionId;
        @NotBlank(message = "请输入发布标题")
        @Size(max = 255, message = "发布标题不能超过 255 字")
        private String title;
        @Size(max = 1000, message = "选题不能超过 1000 字")
        private String topic;
        /** Existing content classification dictionary; daily is used when omitted by the new editor. */
        @Size(max = 64, message = "内容分类不能超过 64 字")
        private String contentClassValue;
        @Size(max = 255, message = "分类名称不能超过 255 字")
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
        @Size(max = 1024, message = "参考作品链接不能超过 1024 字")
        private String referenceWorkUrl;
        /** 从素材库多选出的参考素材快照，随作品保存供审批查看。 */
        @Size(max = 20, message = "参考素材最多选择 20 项")
        private List<@Valid ReferenceMaterial> referenceMaterials;
        private String deliverableUrl;
        private String deliverableSnapshotJson;
        private String scriptText;
        private String detailUrl;
        private String commentHook;
        private String leadResourceUrl;
        private LocalDateTime plannedPublishAt;
        @Size(max = 128, message = "提交请求标识不能超过 128 字")
        private String idempotencyKey;
    }

    /** 素材库参考素材的选择结果，只保存进入审批所需的展示字段。 */
    @Data
    public static class ReferenceMaterial {
        @NotNull(message = "请选择有效参考素材")
        private Long materialId;
        private Long materialVersionId;
        @Size(max = 64, message = "素材编号不能超过 64 字")
        private String materialNo;
        @Size(max = 255, message = "素材标题不能超过 255 字")
        private String title;
        @Size(max = 128, message = "素材类型名称不能超过 128 字")
        private String materialTypeName;
        @Size(max = 1024, message = "素材封面链接不能超过 1024 字")
        private String coverPreviewUrl;
    }
}
