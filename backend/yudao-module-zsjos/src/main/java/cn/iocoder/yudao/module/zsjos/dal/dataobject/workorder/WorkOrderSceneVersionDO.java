package cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** Immutable published work-order template snapshot. */
@TableName("zsjos_work_order_scene_version")
@KeySequence("zsjos_work_order_scene_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderSceneVersionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long sceneId;
    private Integer versionNo;
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
    private String fieldsJson;
    private Long publishedBy;
    private LocalDateTime publishedAt;
}
