package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_registration_route_option")
@KeySequence("zsjos_registration_route_option_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RegistrationRouteOptionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long versionId;
    private String optionKey;
    private Long departmentId;
    private String departmentNameSnapshot;
    private String assigneeType;
    private Integer sort;
    private Boolean enabled;
    private Boolean systemRequired;
}
