package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class StudentServiceRespVO {
    private java.util.List<cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.StudentTaskReminderRespVO> pendingTasks;
    private Long serviceRelationId;
    private Long personId;
    private String personNo;
    private String name;
    private String mobile;
    private String wechatId;
    private Boolean inServicePeriod;
    private Long leadId;
    private String leadNo;
    private LocalDateTime activatedAt;
    private MyStudentRespVO.ServiceVO service;
    private StudentServiceSummaryVO summary;
}
