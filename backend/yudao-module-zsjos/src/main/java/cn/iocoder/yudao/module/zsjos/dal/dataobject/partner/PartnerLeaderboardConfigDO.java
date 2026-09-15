package cn.iocoder.yudao.module.zsjos.dal.dataobject.partner;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@TableName("zsjos_partner_leaderboard_config")
@Data @Accessors(chain = true) @EqualsAndHashCode(callSuper = true)
public class PartnerLeaderboardConfigDO extends TenantBaseDO {
    @TableId private Long id;
    private Boolean enabled;
    private Boolean includeEmployeeSubmitter;
    private String employeeRoleCodes;
    private String enabledTypes;
    private String defaultType;
    private String defaultPeriod;
    private Integer pageSize;
    private Boolean maskName;
}
