package cn.iocoder.yudao.module.system.dal.dataobject.ip;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("system_area")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
public class AreaDO extends BaseDO {

    @TableId
    private Integer id;
    private String name;
    private String selectionCode;
    private Integer type;
    private Integer parentId;
    private Integer sort;
    private Integer status;
    private Boolean leafSelectable;

    public String getSelectionCode() {
        return selectionCode != null ? selectionCode : String.valueOf(id);
    }

}
