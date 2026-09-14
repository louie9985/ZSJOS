package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.util.*;
import java.time.LocalDateTime;

@Data
public class MediaAccountProfileVO {
    private MediaAccountRespVO account;
    private MediaAccountFieldConfigRespVO.VersionVO config;
    private Map<String, Object> values;
    private List<MediaAccountDetailSnapshotVO> snapshots;
    private List<String> editableFields;
    private List<String> missingFields;
    private Map<String, Integer> missingByOwner;
    private Map<String, String> sourceNotes;
    private Map<String, FileVO> files;
    private String studentName;
    private String currentUserName;
    private String directorName;
    private String operatorName;
    private boolean canViewHistory;
    private boolean canSubmitPositioning;
    /** Read-only metrics projected from the student's unique partner account. */
    private PartnerMetrics partnerMetrics;

    @Data public static class PartnerMetrics {
        private Long partnerId;
        private String sourceStatus;
        private Long totalLeads;
        private Long monthLeads;
        private Long totalDeals;
        private Long monthDeals;
        private java.math.BigDecimal totalDealAmount;
        private java.math.BigDecimal monthDealAmount;
        private java.math.BigDecimal totalDealRate;
        private java.math.BigDecimal monthDealRate;
    }
    @Data public static class PositioningSnapshot {
        private Long configVersionId;
        private List<MediaAccountFieldConfigRespVO.FieldVO> fields;
        private List<MediaAccountDetailSnapshotVO> values;
        private List<FileVO> files;
    }
    @Data public static class Patch {
        @NotNull @PositiveOrZero private Integer version;
        @NotNull private Long configVersionId;
        @NotBlank @Size(max=128) private String idempotencyKey;
        @NotNull @Size(max=200) private Map<String, Object> changes;
    }
    @Data public static class RecordRequest {
        @NotNull @PositiveOrZero private Integer version;
        @NotNull private Long configVersionId;
        @NotBlank @Size(max=64) private String fieldKey;
        @NotBlank @Size(max=128) private String idempotencyKey;
        @Size(max=10000) private String content;
        @Size(max=20) private List<@NotNull Long> fileIds;
    }
    @Data public static class DiagnosisRequest {
        @NotNull @PositiveOrZero private Integer cycle;
        @NotNull @PositiveOrZero private Integer version;
        @NotNull private Long configVersionId;
        @NotBlank @Size(max=128) private String idempotencyKey;
        @NotBlank private String templateType;
        @NotBlank private String currentStage;
        @NotBlank private String accountStatus;
        @NotBlank private String cooperationLevel;
        @NotBlank private String cooperationEvidence;
        @NotBlank private String primaryProblem;
        @NotBlank private String primaryProblemEvidence;
        @NotBlank private String secondaryProblem;
        @NotBlank private String secondaryProblemEvidence;
        @NotBlank private String conclusion;
        @NotBlank private String improvementMeasures;
        @NotBlank private String observedData;
        @NotNull private Boolean reposition;
    }
    @Data public static class Entry {
        private Long id;
        private String kind;
        private String fieldKey;
        private String title;
        private String content;
        private List<MediaAccountDetailSnapshotVO> snapshots;
        private List<FileVO> files;
        private String operatedBy;
        private LocalDateTime operatedAt;
        private Integer resultVersion;
        private PositioningSnapshot positioning;
    }
    @Data public static class FileVO {
        private Long id;
        private String name;
        private String type;
        private Long size;
        private String previewUrl;
    }
}
