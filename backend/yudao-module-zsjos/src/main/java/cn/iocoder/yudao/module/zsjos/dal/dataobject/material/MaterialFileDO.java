package cn.iocoder.yudao.module.zsjos.dal.dataobject.material;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_material_file")
@KeySequence("zsjos_material_file_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialFileDO extends TenantBaseDO {
    @TableId private Long id;
    private Long materialVersionId;
    private String fieldKey;
    private Integer groupIndex;
    private Long infraFileId;
    private String fileUrlSnapshot;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private Long uploadedByUserId;
}
