package cn.iocoder.yudao.module.eam.dal.dataobject.procurement;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("eam_demand")
@KeySequence("eam_demand_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamDemandDO extends BaseDO {
    @TableId
    private Long id;
    private String no;
    private Long employeeId;
    private Long applicantUserId;
    private Long applicantDeptId;
    private Integer status;
    private String processInstanceId;
    private String reason;
}
