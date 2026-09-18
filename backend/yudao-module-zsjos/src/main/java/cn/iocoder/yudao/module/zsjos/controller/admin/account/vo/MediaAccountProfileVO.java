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
    private Map<String, Entry> latestRecords;
    /** Frozen requirements copied from the latest evidence-complete positioning card. */
    private Map<String, Object> positioningRequirements;
    private Map<String, Object> diagnosisContext;
    private boolean canViewHistory;
    private boolean canSubmitDiagnosis;
    private boolean canStartDiagnosis;
    private boolean diagnosisStarted;
    private PartnerMetrics partnerMetrics;
    public record DiagnosisTodo(Long taskId, Long accountId, Long studentPersonId, String accountName, String title,
                                String templateType, Integer cycle, LocalDateTime dueAt, Map<String,Object> payload) {}
    @Data public static class ReminderAck {
        @NotEmpty @Size(max=500) private List<@NotNull Long> taskIds;
    }
    @Data public static class PartnerMetrics { private Long partnerId,totalLeads,monthLeads,totalDeals,monthDeals; private java.math.BigDecimal totalDealAmount=java.math.BigDecimal.ZERO,monthDealAmount=java.math.BigDecimal.ZERO,totalDealRate=java.math.BigDecimal.ZERO,monthDealRate=java.math.BigDecimal.ZERO; private String sourceStatus; }
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
    @Data public static class HistoryQuery extends cn.iocoder.yudao.framework.common.pojo.PageParam {
        @Size(max=64) private String fieldKey;
        @Size(max=32) private String kind;
        @Positive private Integer cycle;
    }
    @Data public static class DiagnosisRequest {
        private Long previousEntryId;
        private Long taskId;
        @NotNull @PositiveOrZero private Integer cycle;
        @NotNull @PositiveOrZero private Integer version;
        @NotNull private Long configVersionId;
        @NotBlank @Size(max=128) private String idempotencyKey;
        @NotBlank private String templateType;
        @NotBlank private String currentStage;
        @NotBlank private String accountStatus;
        @Size(max=2000) private String cooperationLevel;
        @Size(max=2000) private String cooperationEvidence;
        @NotBlank private String primaryProblem;
        @NotBlank @Size(max=2000) private String primaryProblemEvidence;
        @Size(max=2000) private String secondaryProblem;
        @Size(max=2000) private String secondaryProblemEvidence;
        @NotBlank @Size(max=2000) private String conclusion;
        @Size(max=2000) private String improvementMeasures;
        @Size(max=2000) private String observedData;
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
    }
    @Data public static class FileVO {
        private Long id;
        private String name;
        private String type;
        private Long size;
        private String previewUrl;
    }
}
