package cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_work_change")
@KeySequence("zsjos_work_change_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkChangeDO extends TenantBaseDO {
    @TableId private Long id;
    private String subjectType;
    private Long subjectId;
    private String changeType;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String reason;
    private Long operatorUserId;
    private LocalDateTime changedAt;
}
