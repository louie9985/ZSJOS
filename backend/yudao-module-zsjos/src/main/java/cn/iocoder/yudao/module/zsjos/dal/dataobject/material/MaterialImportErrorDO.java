package cn.iocoder.yudao.module.zsjos.dal.dataobject.material;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_material_import_error")
@KeySequence("zsjos_material_import_error_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialImportErrorDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long batchId;
    private String sheetName;
    private Integer rowNo;
    private String fieldKey;
    private String errorCode;
    private String errorMessage;
    private String rowSnapshotJson;
}
