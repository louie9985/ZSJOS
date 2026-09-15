package cn.iocoder.yudao.module.zsjos.controller.admin.content.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContentVersionSaveReqVO {
    @NotNull private Long contentId;
    /** Deprecated input retained for wire compatibility; the server derives stage from the content state. */
    private String stage;
    @Size(max = 255) private String titleSnapshot;
    @Size(max = 1000) private String topicSnapshot;
    private String coverSnapshotJson;
    private String materialRefsJson;
    private Long referenceContentVersionId;
    /** 参考作品链接，允许任意可读链接文本。 */
    @Size(max = 1024) private String referenceWorkUrl;
    private String deliverableUrl;
    private String deliverableSnapshotJson;
    private String scriptText;
    /** 内容生产审批扩展字段，均随版本保存，避免读取当前配置覆盖历史。 */
    private String purposeValue;
    private String purposeLabelSnapshot;
    private String formatValue;
    private String formatLabelSnapshot;
    private String detailUrl;
    private String commentHook;
    private String leadResourceUrl;
    private LocalDateTime plannedPublishAt;
    @Size(max = 128) private String idempotencyKey;
}


