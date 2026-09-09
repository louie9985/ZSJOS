package cn.iocoder.yudao.module.zsjos.dal.dataobject.material;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_material_version")
@KeySequence("zsjos_material_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialVersionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long materialId;
    private Integer versionNo;
    private Long schemaVersionId;
    private String status;
    private String title;
    private String coverSnapshotJson;
    private String summary;
    private String valuesJson;
    private String fieldSnapshotJson;
    private String dictSnapshotJson;
    private String fileSnapshotJson;
    private String searchText;
    private String contentHash;
    private String processInstanceId;
    private String processDefinitionId;
    private String processDefinitionKey;
    private Integer processDefinitionVersion;
    private String businessKey;
    private Long submittedByUserId;
    private LocalDateTime submittedAt;
    private LocalDateTime effectiveAt;
    private LocalDateTime rejectedAt;
    private String rejectionReason;
    private Integer version;
}
