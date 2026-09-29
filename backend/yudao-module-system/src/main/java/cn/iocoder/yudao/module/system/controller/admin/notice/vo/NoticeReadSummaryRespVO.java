package cn.iocoder.yudao.module.system.controller.admin.notice.vo;

import lombok.Data;
import java.util.List;

@Data
public class NoticeReadSummaryRespVO {
    private Boolean published;
    private Boolean rosterComplete;
    private Long expectedCount;
    private Long readCount;
    private Long unreadCount;
    private Double readRate;
    private Long extraReadCount;
    private Long actualReadCount;
    private List<Department> departments = List.of();
    private List<Department> extraDepartments = List.of();
    @Data
    public static class Department {
        private Long id;
        private String name;
    }
}
