package cn.iocoder.yudao.module.zsjos.dal.dataobject.navigation;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_navigation_preference")
@Data
@EqualsAndHashCode(callSuper = true)
public class NavigationPreferenceDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private String client;
    private String preferenceJson;
}
