package cn.iocoder.yudao.module.zsjos.dal.dataobject.collaboration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_collaboration_group")
@KeySequence("zsjos_collaboration_group_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CollaborationGroupDO extends TenantBaseDO {
    @TableId private Long id;
    private Long sourceServiceRelationId;
    private Long studentPersonId;
    private Long directorUserId;
    private Long operatorUserId;
    private String status;
    private Integer version;
}
