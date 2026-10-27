package cn.iocoder.yudao.module.zsjos.dal.dataobject.registration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("zsjos_student_service_annotation")
@KeySequence("zsjos_student_service_annotation_seq")
public class StudentServiceAnnotationDO extends TenantBaseDO {
    @TableId private Long id;
    private Long serviceRelationId;
    private String proxyStudent;
    private String proxyStudentLabel;
    private String agentName;
    private String serviceNeeded;
    private String serviceNeededLabel;
    private Integer version;
}
