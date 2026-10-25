package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper=true) @TableName("zsjos_student_entry_command")
public class StudentEntryCommandDO extends TenantBaseDO {
    @TableId private Long id;
    private Long operatorUserId;
    private String commandKey;
    private String requestHash;
    private Long personId;
    private Long serviceRelationId;
}
