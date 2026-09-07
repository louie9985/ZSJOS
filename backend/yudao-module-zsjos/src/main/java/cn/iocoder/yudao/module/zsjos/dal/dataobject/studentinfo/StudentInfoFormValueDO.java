package cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "zsjos_student_info_form_value", autoResultMap = true)
public class StudentInfoFormValueDO extends TenantBaseDO {
    private Long id, formId;
    private String fieldKey, fieldType, dictType, valueCode, valueLabelSnapshot, areaCodePath, areaLabelSnapshot;
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler.class)
    @lombok.ToString.Exclude
    private String valueText;
    @com.baomidou.mybatisplus.annotation.TableField("`sensitive`")
    private Boolean sensitive;
}
