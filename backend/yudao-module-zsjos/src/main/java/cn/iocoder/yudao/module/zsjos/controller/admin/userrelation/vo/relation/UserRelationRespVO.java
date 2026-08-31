package cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.relation;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserRelationRespVO extends LeadAssignmentUserRespVO {

    private List<LeadAssignmentUserRespVO> targetUsers;
    private Integer validTargetCount;
    private Integer invalidTargetCount;
    private LocalDateTime updateTime;

}
