package cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_work_order_scene")
@KeySequence("zsjos_work_order_scene_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderSceneDO extends TenantBaseDO {
    @TableId private Long id;
    private String code;
    private String name;
    private String remark;
    private String categoryValue;
    private String categoryLabelSnapshot;
    private String icon;
    private Integer sort;
    private String processorType;
    private String allowedAssignmentTypesJson;
    private String sourceQualificationMode;
    private String sourceRoleScopesJson;
    private String sourceDeptScopesJson;
    private String targetQualificationMode;
    private String targetRoleScopesJson;
    private String targetDeptScopesJson;
    private String rejectionStrategy;
    private String numberPrefix;
    private String numberResetPeriod;
    private Integer numberSequenceWidth;
    private String lifecycleStatus;
    private Long publishedVersionId;
    private Integer publishedVersionNo;
    private String sourcePostCode;
    private String targetPostCode;
    private String assignmentMode;
    private String fieldsJson;
    private Integer status;
    @Version private Integer version;
}
