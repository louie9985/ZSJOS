package cn.iocoder.yudao.module.zsjos.dal.dataobject.material;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_material_schema_version")
@KeySequence("zsjos_material_schema_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialSchemaVersionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long materialTypeId;
    private Integer versionNo;
    private String status;
    private String fieldsJson;
    private String schemaHash;
    private Long publishedByUserId;
    private LocalDateTime publishedAt;
    private Integer version;
}
