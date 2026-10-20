package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class StudentFirstContactProgressVO {
    private Long recordId;
    private Long configVersionId;
    private String source;
    private Boolean historyIncomplete;
    private LocalDateTime submittedAt;
    private Long operatorUserId;
    private String operatorUserName;
    private Boolean studyPlanCompletedAtFirstContact;
    private String studyPlanStatus;
    private LocalDateTime studyPlanCompletedAt;
    private List<Item> items;
    @Data public static class Item {
        private String key;
        private String title;
        private Integer sort;
        private Boolean attachmentRequired;
        private Boolean completed;
        private List<Long> attachmentFileIds;
    }
}
