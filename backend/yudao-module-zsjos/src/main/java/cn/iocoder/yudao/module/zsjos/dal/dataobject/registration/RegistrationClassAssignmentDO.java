package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_registration_class_assignment")
@KeySequence("zsjos_registration_class_assignment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RegistrationClassAssignmentDO extends TenantBaseDO {
    @TableId private Long id;
    private Long registrationCaseId;
    private Long orderItemId;
    private Long classId;
    private String classNoSnapshot;
    private String classNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long homeroomUserId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String homeroomUserNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long categoryId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String categoryNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String categoryPathSnapshot;
    private Long updatedByUserId;
    private Integer version;
}
