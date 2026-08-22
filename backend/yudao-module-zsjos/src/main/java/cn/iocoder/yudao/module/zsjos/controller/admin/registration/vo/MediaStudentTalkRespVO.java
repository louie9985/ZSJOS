package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MediaStudentTalkRespVO {
    private Long id;
    private Long accountId;
    private Long operatorUserId;
    private String operatorUserName;
    private String content;
    private List<Long> attachmentFileIds;
    private LocalDateTime occurredAt;
}
