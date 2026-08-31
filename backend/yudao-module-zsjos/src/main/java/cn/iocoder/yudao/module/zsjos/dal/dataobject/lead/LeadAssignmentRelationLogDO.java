package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_user_relation_log")
@KeySequence("zsjos_user_relation_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadAssignmentRelationLogDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String scene;
    private String sourceUserIds;
    private String targetUserIds;
    private String actionType;
    private Long operatorUserId;

}
