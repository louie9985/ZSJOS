package cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_work_order_attachment")
@KeySequence("zsjos_work_order_attachment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderAttachmentDO extends TenantBaseDO {
    @TableId private Long id;
    private Long workOrderId;
    private Integer roundNo;
    private String phase;
    private Long fileId;
    private String fileNameSnapshot;
    private String mimeTypeSnapshot;
    private Long fileSizeSnapshot;
    private Integer sort;
}
