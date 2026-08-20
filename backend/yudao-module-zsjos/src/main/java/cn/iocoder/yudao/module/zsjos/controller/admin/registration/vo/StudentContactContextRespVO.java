package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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
    private Long contentDirectorUserId;
    private String contentDirectorUserName;
    private Long careerPlannerUserId;
    private String careerPlannerUserName;

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
}
