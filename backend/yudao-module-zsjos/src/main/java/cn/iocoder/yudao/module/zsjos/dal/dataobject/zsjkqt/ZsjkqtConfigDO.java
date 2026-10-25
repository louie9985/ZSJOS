package cn.iocoder.yudao.module.zsjos.dal.dataobject.zsjkqt;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("zsjkqt_config")
public class ZsjkqtConfigDO extends TenantBaseDO {
    @TableId private Long id;
    private String configKey;
    private String valueJson;
    private Integer version;
}
