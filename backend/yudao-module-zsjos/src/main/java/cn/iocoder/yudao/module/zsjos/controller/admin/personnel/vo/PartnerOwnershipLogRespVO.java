package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PartnerOwnershipLogRespVO {
    private Long id;
    private String previousEmployeeName;
    private String employeeName;
    private String actionType;
    private String reason;
    private String operatorName;
    private LocalDateTime occurredAt;
}
