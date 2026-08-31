package cn.iocoder.yudao.module.zsjos.dal.mysql.personnel;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SubordinatePartnerRow {
    private Long id;
    private String partnerNo;
    private String name;
    private String mobile;
    private String status;
    private Long boundSystemUserId;
    private String channelId;
    private LocalDateTime enabledAt;
    private LocalDateTime disabledAt;
    private Long assignedEmployeeUserId;
    private String assignedEmployeeName;
    private LocalDateTime assignedAt;
    private Integer assignmentVersion;
}
