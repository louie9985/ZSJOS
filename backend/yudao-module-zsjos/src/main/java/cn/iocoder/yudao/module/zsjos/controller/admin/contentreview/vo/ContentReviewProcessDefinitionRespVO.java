package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import lombok.Data;

import java.util.List;

@Data
public class ContentReviewProcessDefinitionRespVO {
    private String id;
    private String key;
    private String name;
    private Integer version;
    private String category;
    private Boolean suspended;
    private Boolean simpleSequentialApproval;
    private List<UserTask> userTasks;

    @Data
    public static class UserTask {
        private String key;
        private String name;
        private String executionMode;
        private List<String> nextUserTaskKeys;
    }
}
