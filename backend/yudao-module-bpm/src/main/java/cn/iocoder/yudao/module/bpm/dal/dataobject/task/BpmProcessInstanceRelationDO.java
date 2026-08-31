package cn.iocoder.yudao.module.bpm.dal.dataobject.task;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("bpm_process_instance_relation")
@KeySequence("bpm_process_instance_relation_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class BpmProcessInstanceRelationDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String sourceProcessInstanceId;
    private String targetProcessInstanceId;
    private String formField;
    private Integer sort;
    private String targetNameSnapshot;
    private String targetProcessDefinitionIdSnapshot;
    private String targetProcessDefinitionNameSnapshot;
    private String targetDisplayNoSnapshot;
    private String targetBusinessKeySnapshot;
    private String targetStartUserNameSnapshot;
    private LocalDateTime targetStartTimeSnapshot;

}
