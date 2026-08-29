package cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ForcedFormSubmissionRespVO {

    private Long id;
    private Long formId;
    private String formName;
    private Long versionId;
    private Integer version;
    private Long userId;
    private String userNickname;
    private String fieldsSnapshotJson;
    private String answersJson;
    private String dictSnapshotJson;
    private String platform;
    private LocalDateTime createTime;

}
