package cn.iocoder.yudao.module.system.dal.dataobject.notify;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@TableName(value = "system_notify_rule", autoResultMap = true)
@KeySequence("system_notify_rule_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotifyRuleDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String name;
    private String sceneCode;
    private Long templateId;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<String> recipientRoles;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Long> specifiedUserIds;
    private String actionType;
    private Integer status;
}
