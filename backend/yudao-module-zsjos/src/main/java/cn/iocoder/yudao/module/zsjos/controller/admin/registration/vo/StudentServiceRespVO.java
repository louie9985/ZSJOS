package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class StudentServiceRespVO {
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
}
