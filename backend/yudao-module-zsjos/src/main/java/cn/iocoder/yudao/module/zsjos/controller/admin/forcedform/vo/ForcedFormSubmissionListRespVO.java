package cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ForcedFormSubmissionListRespVO {

    private Long id;
    private Long formId;
    private String formName;
    private Long versionId;
    private Integer version;
    private Long userId;
    private String userNickname;
    private String platform;
    private LocalDateTime createTime;

}
