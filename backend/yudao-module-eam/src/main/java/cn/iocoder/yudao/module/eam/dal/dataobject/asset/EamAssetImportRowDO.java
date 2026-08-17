package cn.iocoder.yudao.module.eam.dal.dataobject.asset;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("eam_asset_import_row")
@KeySequence("eam_asset_import_row_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EamAssetImportRowDO extends BaseDO {

    @TableId
    private Long id;
    private Long batchId;
    private String fileHash;
    private String sheetName;
    private Integer rowNum;
    private Long assetId;
    private String assetCode;
    private Integer importAction;

}
