package cn.iocoder.yudao.module.eam.controller.admin.procurement.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class EamDemandRespVO {
    private Long id;
    private String no;
    private Long employeeId;
    private Long applicantUserId;
    private Long applicantDeptId;
    private Integer status;
    private String processInstanceId;
    private String reason;
    private LocalDateTime createTime;
    private List<EamDemandItemRespVO> items;
}
