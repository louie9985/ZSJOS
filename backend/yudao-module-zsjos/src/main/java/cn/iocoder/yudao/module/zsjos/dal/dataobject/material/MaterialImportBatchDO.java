package cn.iocoder.yudao.module.zsjos.dal.dataobject.material;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_material_import_batch")
@KeySequence("zsjos_material_import_batch_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialImportBatchDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String batchNo;
    private Long materialTypeId;
    private Long schemaVersionId;
    private String sourceFileName;
    private String sourceFileHash;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failureCount;
    private String previewRowsJson;
    private String idempotencyKey;
    private Long createdByUserId;
    private Long confirmedByUserId;
    private LocalDateTime confirmedAt;
    private Integer version;
}
