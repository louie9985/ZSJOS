package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class StudentContactRecordRespVO {
    private Long id;
    private String contactType;
    private Boolean successful;
    private String unsuccessfulReasonValue;
    private String unsuccessfulReasonLabel;
    private String remark;
    private List<Long> attachmentFileIds;
    private List<String> completedChecklistKeys;
    private LocalDateTime nextContactAt;
    private Long operatorUserId;
    private String operatorUserName;
    private LocalDateTime submittedAt;
}
