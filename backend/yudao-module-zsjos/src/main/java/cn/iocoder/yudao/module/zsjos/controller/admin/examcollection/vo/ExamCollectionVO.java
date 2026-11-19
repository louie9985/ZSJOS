package cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

public final class ExamCollectionVO {
    private ExamCollectionVO() {}
    @Data public static class UploadLimit {
        @NotNull @Min(1) @Max(100) private Integer maxSizeMb;
        @NotNull @Min(1) @Max(9) private Integer maxImages;
    }
    @Data public static class Create {
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{16,64}") private String idempotencyKey;
        @NotBlank @Size(max=200) private String title;
        @Size(max=2000) private String description;
        @Size(max=200000) private String content;
        @NotNull @Size(max=12) private List<@NotBlank String> selectedFields;
        @Size(max=12) private Map<String, @NotNull @Valid UploadLimit> uploadLimits;
        @Size(max=30) private List<@NotNull @Valid Field> customFields;
        @Size(max=45) private Map<@NotBlank String, @Size(max=500) String> fieldNotes;
    }
    @Data @EqualsAndHashCode(callSuper=true) public static class Query extends PageParam {
        @Size(max=200) private String keyword;
        @Pattern(regexp="OPEN|COMPLETED") private String status;
    }
    @Data public static class Task {
        private Long id;
        private String title, description, content, status;
        private Integer revision;
        private LocalDateTime createdAt, completedAt;
        private String completionReason;
        private long expected, collected, exceptions, additional;
    }
    @Data @EqualsAndHashCode(callSuper=true) public static class Detail extends Task {
        private List<Field> fields;
        private List<Long> classIds = List.of();
        private String url;
    }
    @Data public static class Notify {
        @NotNull @Min(0) private Integer revision;
        @NotNull @Size(min=1,max=100) private List<@NotNull Long> classIds;
    }
    @Data public static class Transition {
        @NotNull @Min(0) private Integer revision;
        @Size(max=500) private String reason;
    }
    public record ClassOption(Long id, String name, Long plannerId, String plannerName, Long groupId, String groupName, boolean selectable) {}
    @Data public static class ClassChange {
        @NotNull @Min(0) private Integer revision;
        @NotNull @Size(min=1,max=100) private List<@NotNull Long> classIds;
        @NotBlank @Size(max=500) private String reason;
    }
    @Data @EqualsAndHashCode(callSuper=true) public static class SubmissionQuery extends PageParam {
        @Pattern(regexp="ARCHIVED|EXCEPTION|INVALID") private String status;
        private Long serviceRelationId;
    }
    @Data public static class Submission {
        private Long id, serviceRelationId, sequenceNo;
        private Integer revision;
        private String status, exceptionCode;
        private LocalDateTime submittedAt;
        private Map<String, String> values;
    }
    @Data @EqualsAndHashCode(callSuper=true) public static class SubmissionDetail extends Submission {
        private List<Field> fields;
        private List<cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Image> attachments;
        private long pendingGroupCount;
        private Long latestPendingSubmissionId;
        private LocalDateTime latestPendingSubmittedAt;
    }
    @Data @EqualsAndHashCode(callSuper=true) public static class CandidateQuery extends PageParam {
        @Size(max=200) private String keyword;
        private Long classId;
    }
    public record Candidate(Long serviceRelationId, Long personId, String studentName, String mobile,
                            Long classId, String className, String serviceStatus) {}
    @Data public static class Resolve {
        @NotNull @Min(0) private Integer revision;
        @NotNull private Long serviceRelationId;
        @NotNull @Min(0) private Integer archiveRevision;
        @NotBlank @Size(max=500) private String reason;
    }
    @Data public static class Invalidate {
        @NotNull @Min(0) private Integer revision;
        @NotBlank @Size(max=500) private String reason;
    }
    @Data public static class Correction {
        @NotNull @Min(0) private Integer revision, taskRevision, bindingRevision, sourceArchiveRevision, targetArchiveRevision;
        @NotNull private Long serviceRelationId, latestSubmissionId;
        @NotBlank @Size(max=500) private String reason;
    }
    @Data public static class CorrectionPreview {
        private String sourceStudentName, targetStudentName;
        private Integer revision, taskRevision, bindingRevision;
        private Long latestSubmissionId;
        private long submissionCount;
        private Archive sourceArchive, restoredArchive, targetArchive;
        private List<String> restoredFields, retainedFields;
    }
    @Data public static class Archive {
        private Long serviceRelationId;
        private Integer revision;
        private List<Field> fields;
        private Map<String, String> values;
        private List<ArchiveAttachment> attachments;
    }
    public record ArchiveAttachment(String source, Long id, String fieldKey, String side, String name, String contentType, Long size) {}
    public record Event(Long id, Long submissionId, String action, String reason, String operatorName, LocalDateTime time) {}
    @Data public static class RosterChange {
        @NotNull @Min(0) private Integer revision;
        @NotNull @Size(max=500) private List<@NotNull Long> addServiceIds = List.of();
        @NotNull @Size(max=500) private List<@NotNull Long> removeServiceIds = List.of();
        @NotBlank @Size(max=500) private String reason;
    }
    @Data @EqualsAndHashCode(callSuper=true) public static class MemberQuery extends PageParam {
        private Boolean active = true;
        private Boolean filled;
        @Size(max=200) private String keyword;
    }
    public record Member(Long serviceRelationId, Long personId, String studentName, String mobile,
                         Long classId, String className, String plannerName, boolean active, Long lastSubmissionId,
                         String archiveName, String archiveMobile, String archiveIdCard, String absenceReason) {}
}
