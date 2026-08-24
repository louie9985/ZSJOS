package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class StudentContactConfigRespVO {
    private VersionVO published;
    private VersionVO draft;
    @Data public static class VersionVO {
        private Long id;
        private Integer versionNo;
        private Integer version;
        private Integer firstContactTimeoutMinutes;
        private Integer studyPlanTimeoutMinutes;
        private List<ChecklistItemVO> checklist;
        private List<String> quickNotes;
        private Map<String, List<String>> collaboratorTabs;
    }
    @Data public static class ChecklistItemVO {
        private String key;
        private String title;
        private String type;
        private Boolean enabled;
        private Boolean attachmentRequired;
        private Integer sort;
    }
}
