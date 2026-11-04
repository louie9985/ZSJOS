package cn.iocoder.yudao.module.system.dal.dataobject.ip;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** Versioned public reference data; separate from administrator-selectable current areas. */
@Data
@TenantIgnore
@TableName("system_identity_area")
public class IdentityAreaDO {
    @TableId
    private Long id;
    private String code;
    private String provinceName;
    private String cityName;
    private String name;
    private String recordStatus;
    private String validFrom;
    private String validUntil;
    private String successorCodes;
    private String dataVersion;
}
