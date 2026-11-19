package cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("zsjos_enrollment_category_agreement")
public class EnrollmentAgreementDO extends TenantBaseDO {
    private Long id;
    private Long revision;
    private Long publishedVersion;
    private String draftBody;
    private String publishedBody;
}
