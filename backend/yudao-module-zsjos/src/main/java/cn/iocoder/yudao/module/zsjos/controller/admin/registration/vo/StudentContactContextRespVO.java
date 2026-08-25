package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class StudentContactContextRespVO {
    private Long serviceRelationId;
    private String acceptanceStatus;
    private LocalDateTime acceptedAt;
    private Integer version;
    private CurrentTaskVO currentTask;
    private List<ChecklistItemVO> firstContactChecklist;
    private List<String> quickNotes;
    private Integer firstContactTimeoutMinutes;
    private Integer studyPlanTimeoutMinutes;
    private List<String> visibleTabs;
    private List<String> availableActions;
    private Long ownerUserId;
    private String ownerUserName;
    private Long contentDirectorUserId;
    private String contentDirectorUserName;
    private Long careerPlannerUserId;
    private String careerPlannerUserName;
    private Long operatorUserId;
    private String operatorUserName;
    private String directorStage;
    private LocalDateTime directorInterviewAt;
    private LocalDateTime defaultDirectorInterviewAt;
    private Integer directorInterviewAppointmentHours;
    private Integer directorTrialDays;
    private String deliveryStage;
    private String deliveryStageLabel;
    private List<DeliveryStageVO> deliveryStages;
    private LocalDate examDate;
    private List<FormFieldVO> formFields;
    private DirectorFormsVO directorForms;
    private Boolean operatorAssignmentConflict;

    @Data public static class CurrentTaskVO {
        private Long id;
        private String type;
        private String status;
        private LocalDateTime dueAt;
    private Boolean overdue;
    }
    @Data public static class ChecklistItemVO {
        private String key;
        private String title;
        private String type;
        private Boolean attachmentRequired;
    }
    @Data public static class DeliveryStageVO {
        private String code;
        private String label;
        private String status;
        private Boolean current;
        private Boolean available;
    }
    @Data public static class FormFieldVO {
        private String key;
        private String title;
        private String type;
        private Boolean required;
        private Integer sort;
        private String description;
        private String dictType;
        private Boolean multiple;
        private Boolean enabled;
        private Boolean systemField;
        private Integer minSelections;
        private Integer maxSelections;
        private Integer minValue;
        private Integer maxValue;
        private Integer maxLength;
        private String group;
    }
    @Data public static class DirectorFormsVO {
        private DirectorFormVO precheck;
        private DirectorFormVO interview;
    }
    @Data public static class DirectorFormVO {
        private String state;
        private Long configId;
        private Integer configVersion;
        private Long templateId;
        private Long templateVersionId;
        private Integer templateVersionNo;
        private List<FormFieldVO> fields;
        private Map<String, Object> values;
        private Map<String, Object> dictSnapshots;
        private LocalDateTime savedAt;
        private Long savedByUserId;
        private LocalDateTime submittedAt;
        private LocalDateTime interviewAt;
    }
}
