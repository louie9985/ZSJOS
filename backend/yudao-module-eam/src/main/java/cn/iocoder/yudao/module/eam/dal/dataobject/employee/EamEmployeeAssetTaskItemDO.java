package cn.iocoder.yudao.module.eam.dal.dataobject.employee;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("eam_employee_asset_task_item")
@KeySequence("eam_employee_asset_task_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamEmployeeAssetTaskItemDO extends BaseDO {
    @TableId
    private Long id;
    private Long taskId;
    private Long assetId;
    private Long holdingId;
    private String assetNameSnapshot;
    private Integer action;
    private Long transferToEmployeeId;
    private Integer status;
    private String remark;
}
