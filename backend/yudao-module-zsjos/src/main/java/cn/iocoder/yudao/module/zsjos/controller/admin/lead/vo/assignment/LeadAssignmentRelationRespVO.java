package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeadAssignmentRelationRespVO extends LeadAssignmentUserRespVO {

    private List<LeadAssignmentUserRespVO> salesUsers;
    private Integer validSalesCount;
    private Integer invalidSalesCount;
    private LocalDateTime updateTime;

}
