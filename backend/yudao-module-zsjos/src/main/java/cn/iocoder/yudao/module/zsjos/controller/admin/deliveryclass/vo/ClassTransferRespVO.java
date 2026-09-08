package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ClassTransferRespVO {
    private Long id;
    private Long serviceRelationId;
    private Long fromClassId;
    private String fromClassNo;
    private String fromClassName;
    private Long targetClassId;
    private String targetClassNo;
    private String targetClassName;
    private Long fromHomeroomUserId;
    private String fromHomeroomUserName;
    private Long targetHomeroomUserId;
    private String targetHomeroomUserName;
    private Long reviewerUserId;
    private String reason;
    private String status;
    private String processInstanceId;
    private LocalDateTime submittedAt;
    private LocalDateTime finishedAt;
    private String resolutionReason;
}
