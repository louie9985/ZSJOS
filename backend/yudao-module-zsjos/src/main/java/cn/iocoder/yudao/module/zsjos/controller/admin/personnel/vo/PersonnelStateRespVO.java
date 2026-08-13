package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PersonnelStateRespVO {
    private Long userId;
    private String state;
    private String reason;
    private Long changedByUserId;
    private LocalDateTime changedAt;
}
