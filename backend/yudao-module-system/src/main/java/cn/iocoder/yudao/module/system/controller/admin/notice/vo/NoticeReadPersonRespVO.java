package cn.iocoder.yudao.module.system.controller.admin.notice.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NoticeReadPersonRespVO {
    private Long userId;
    private String userName;
    private Long deptId;
    private String deptName;
    private String profileSource;
    private Integer accountStatus;
    private Boolean accountDeleted;
    private LocalDateTime readTime;
}
