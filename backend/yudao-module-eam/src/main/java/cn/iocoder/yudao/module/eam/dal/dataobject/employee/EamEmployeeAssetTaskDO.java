package cn.iocoder.yudao.module.eam.dal.dataobject.employee;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("eam_employee_asset_task")
@KeySequence("eam_employee_asset_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamEmployeeAssetTaskDO extends BaseDO {
    @TableId
    private Long id;
    private String eventKey;
    private String latestEventKey;
    private Integer type;
    private Integer status;
    private Long employeeId;
    private Long leaderUserId;
    private String employeeNameSnapshot;
    private Long deptIdSnapshot;
    private String processInstanceId;
    private Long demandId;
    private LocalDateTime plannedLeaveTime;
    private String remark;
}
