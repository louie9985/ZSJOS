package cn.iocoder.yudao.module.system.dal.dataobject.notify;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("system_notify_channel_config")
@KeySequence("system_notify_channel_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class NotifyChannelConfigDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String channelCode;
    private Boolean enabled;
    private String configRef;
    private String maskedConfig;
}
