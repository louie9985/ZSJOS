package cn.iocoder.yudao.module.zsjos.dal.dataobject.zsjkqt;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("zsjkqt_draft")
public class ZsjkqtDraftDO extends TenantBaseDO {
    @TableId private Long id;
    private String clientId;
    private String name;
    private Integer year;
    private Integer month;
    private String sourceFileName;
    private String sourceFileHash;
    private Integer employeeCount;
    private Integer confirmedCount;
    private String snapshotJson;
    private Integer version;
    private String updaterName;
}
