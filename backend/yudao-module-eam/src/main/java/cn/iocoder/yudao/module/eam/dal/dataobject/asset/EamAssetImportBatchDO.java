package cn.iocoder.yudao.module.eam.dal.dataobject.asset;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("eam_asset_import_batch")
@KeySequence("eam_asset_import_batch_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EamAssetImportBatchDO extends BaseDO {

    @TableId
    private Long id;
    private String fileHash;
    private String fileName;
    private String sheetName;
    private Integer totalRows;
    private Integer createCount;
    private Integer updateCount;
    private Integer skipCount;
    private Integer warningCount;
    private Long operatorId;

}
