package cn.iocoder.yudao.module.hrm.api.employee.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Stable employee projection for other modules.
 */
@Data
public class HrmEmployeeRespDTO {

    private Long id;
    private Long userId;
    private String name;
    private Long deptId;
    private Long leaderEmployeeId;
    private Long leaderUserId;
    private Integer entryStatus;
    private Integer status;
    private LocalDateTime entryTime;
    private LocalDateTime leaveTime;

}
