package cn.iocoder.yudao.module.zsjos.dal.dataobject.director;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@TableName("zsjos_director_form_template")
@KeySequence("zsjos_director_form_template_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class DirectorFormTemplateDO extends TenantBaseDO {
    @TableId private Long id;
    private String scene;
    private String templateCode;
    private String name;
    private Boolean defaultTemplate;
    private String status;
    private Long publishedVersionId;
    private Integer version;
}
