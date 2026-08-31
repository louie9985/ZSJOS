package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_registration_case_route")
@KeySequence("zsjos_registration_case_route_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RegistrationCaseRouteDO extends TenantBaseDO {
    @TableId private Long id;
    private Long registrationCaseId;
    private Long routeOptionId;
    private String optionKey;
    private Long departmentId;
    private String departmentNameSnapshot;
    private String assigneeType;
    private Boolean selected;
    private Long assigneeUserId;
    private String assigneeNameSnapshot;
    private Integer sort;
    private Integer version;
}
